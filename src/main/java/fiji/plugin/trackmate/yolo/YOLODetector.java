/*-
 * #%L
 * TrackMate: your buddy for everyday tracking.
 * %%
 * Copyright (C) 2024 - 2025 TrackMate developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package fiji.plugin.trackmate.yolo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.input.Tailer;

import fiji.plugin.trackmate.Logger;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.SpotCollection;
import fiji.plugin.trackmate.detection.SpotGlobalDetector;
import fiji.plugin.trackmate.util.TMUtils;
import fiji.plugin.trackmate.util.cli.CLIUtils;
import fiji.plugin.trackmate.util.cli.CommandBuilder;
import fiji.plugin.trackmate.yolo.YOLOUtils.YOLOTailerListener;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imglib2.Interval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

public class YOLODetector< T extends RealType< T > & NativeType< T > > implements SpotGlobalDetector< T >
{

	final static String BASE_ERROR_MESSAGE = "[YOLO] ";

	private static final String OUTPUT_FOLDER_NAME = "output";

	private static final String YOLO_LOG_FILENAME = "yolo-predict.log";

	private String errorMessage;

	private long processingTime;

	private SpotCollection output;

	private final ImgPlus< T > img;

	private final Interval interval;

	private Logger logger = Logger.VOID_LOGGER;

	private final YOLOCLI cli;

	public YOLODetector(
			final ImgPlus< T > img,
			final Interval interval,
			final YOLOCLI cli )
	{
		this.img = img;
		this.interval = interval;
		this.cli = cli;
	}

	@Override
	public SpotCollection getResult()
	{
		return output;
	}

	@Override
	public boolean checkInput()
	{
		return true;
	}

	@Override
	public boolean process()
	{
		errorMessage = null;
		final long startTime = System.currentTimeMillis();

		/*
		 * Resave input image.
		 */

		final Path imgTmpFolder;
		final Path outputTmpFolder;
		try
		{
			// Tmp image folder.
			imgTmpFolder = Files.createTempDirectory( "TrackMate-YOLO-imgs_" );
			CLIUtils.recursiveDeleteOnShutdownHook( imgTmpFolder );
			logger.setStatus( "Resaving source image" );
			logger.log( "Saving source image to " + imgTmpFolder + "\n" );

			final boolean ok = YOLOUtils.resaveSingleTimePoints( img, interval, imgTmpFolder.toString(), logger );
			if ( !ok )
			{
				errorMessage = BASE_ERROR_MESSAGE + "Problem saving image frames to " + imgTmpFolder + "\n";
				processingTime = System.currentTimeMillis() - startTime;
				return false;
			}

			// Tmp output folder.
			outputTmpFolder = imgTmpFolder.resolve( OUTPUT_FOLDER_NAME );
		}
		catch ( final IOException e )
		{
			errorMessage = BASE_ERROR_MESSAGE + "Could not create temp folder to save input image:\n" + e.getMessage();
			processingTime = System.currentTimeMillis() - startTime;
			return false;
		}

		cli.imageFolder().set( imgTmpFolder.toString() );
		cli.outputFolder().set( outputTmpFolder.toString() );

		// Check validity of the CLI.
		final String error = cli.check();
		final boolean ok = error == null;
		if ( !ok )
		{
			errorMessage = BASE_ERROR_MESSAGE + error;
			processingTime = System.currentTimeMillis() - startTime;
			return false;
		}

		final String executableName = cli.getCommand();

		// Redirect log to logger.
		final int timeIndex = img.dimensionIndex( Axes.TIME );
		final int nFrames;
		if ( timeIndex < 0 )
		{
			nFrames = 1;
		}
		else
		{
			// In the interval, time is always the last.
			final long minT = interval.min( interval.numDimensions() - 1 );
			final long maxT = interval.max( interval.numDimensions() - 1 );
			nFrames = ( int ) ( maxT - minT + 1 );
		}
		final YOLOTailerListener tailerListener = new YOLOTailerListener( logger, nFrames );
		final File logFile = imgTmpFolder.resolve( YOLO_LOG_FILENAME ).toFile();
		final Tailer tailer = Tailer.builder()
				.setFile( logFile )
				.setTailerListener( tailerListener )
				.setDelayDuration( Duration.ofMillis( 200 ) )
				.get();
		Process process;
		try
		{

			/*
			 * Run YOLO.
			 */

			final List< String > cmd = CommandBuilder.build( cli );
			logger.setStatus( "Running " + executableName );
			logger.log( "Running " + executableName + " with args:\n" );
			cmd.forEach( t -> {
				if ( t.contains( File.separator ) )
					logger.log( t + ' ' );
				else
					logger.log( t + ' ', Logger.GREEN_COLOR.darker() );
			} );
			logger.log( "\n" );

			final ProcessBuilder pb = new ProcessBuilder( cmd );
			pb.redirectOutput( ProcessBuilder.Redirect.appendTo( logFile ) );
			pb.redirectError( ProcessBuilder.Redirect.appendTo( logFile ) );

			// Go!
			process = pb.start();
			process.waitFor();

			/*
			 * Get results back and store them in the spot collection.
			 */

			this.output = new SpotCollection();
			final double[] calibration = TMUtils.getSpatialCalibration( img );

			// Regular expression to extract the time-point in the filename:
			// last integer.
			final String patternString = "(\\d+)(?=\\.[^.]+$)";
			final Pattern pattern = Pattern.compile( patternString );

			final Path txtFilesFolder = outputTmpFolder.resolve( "predict/labels" );
			try (final Stream< Path > paths = Files.list( txtFilesFolder ))
			{
				// Get all the txt files
				final List< Path > txtFiles = paths
						.filter( Files::isRegularFile )
						.filter( p -> p.toString().endsWith( ".txt" ) )
						.collect( Collectors.toList() );

				for ( final Path txtFile : txtFiles )
				{
					// Create a matcher for the filename
					final Matcher matcher = pattern.matcher( txtFile.toString() );
					if ( !matcher.find() )
					{
						logger.error( BASE_ERROR_MESSAGE + "Could not find the time-point indication in the filename of file: "
								+ txtFile + ". Skipping.\n" );
						continue;
					}
					final String tStr = matcher.group( 1 );
					// Images are suffixed with 1-index.
					final int t = Integer.parseInt( tStr );
					final List< Spot > spots = YOLOUtils.importResultFile( txtFile.toString(), interval, calibration, logger );

					synchronized ( output )
					{
						output.put( t, spots );
					}
				}

			}
			catch ( final IOException e )
			{
				e.printStackTrace();
			}
		}
		catch ( final IOException e )
		{
			final String msg = e.getMessage();
			if ( msg.matches( ".+error=13.+" ) )
			{
				errorMessage = BASE_ERROR_MESSAGE + "Problem running " + executableName + ":\n"
						+ "The executable does not have the file permission to run.\n";
			}
			else
			{
				errorMessage = BASE_ERROR_MESSAGE + "Problem running " + executableName + ":\n" + e.getMessage();
			}
			try
			{
				errorMessage = errorMessage + '\n' + new String( Files.readAllBytes( logFile.toPath() ) );
			}
			catch ( final IOException e1 )
			{}
			e.printStackTrace();
			processingTime = System.currentTimeMillis() - startTime;
			return false;
		}
		catch ( final Exception e )
		{
			errorMessage = BASE_ERROR_MESSAGE + "Problem running " + executableName + ":\n" + e.getMessage();
			try
			{
				errorMessage = errorMessage + '\n' + new String( Files.readAllBytes( logFile.toPath() ) );
			}
			catch ( final IOException e1 )
			{}
			e.printStackTrace();
			processingTime = System.currentTimeMillis() - startTime;
			return false;
		}
		finally
		{
			tailer.close();
			process = null;
		}

		processingTime = System.currentTimeMillis() - startTime;
		return true;
	}

	@Override
	public String getErrorMessage()
	{
		return errorMessage;
	}

	@Override
	public long getProcessingTime()
	{
		return processingTime;
	}

	@Override
	public void setLogger( final Logger logger )
	{
		this.logger = logger;
	}
}
