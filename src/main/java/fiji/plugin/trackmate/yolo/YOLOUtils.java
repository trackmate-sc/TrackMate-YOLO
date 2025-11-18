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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.input.TailerListenerAdapter;

import fiji.plugin.trackmate.Logger;
import fiji.plugin.trackmate.Spot;
import ij.IJ;
import ij.ImagePlus;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imglib2.Interval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.display.imagej.ImgPlusViews;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Intervals;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class YOLOUtils
{

	/**
	 * Resaves the specified image, one file per time-point, so that it can be
	 * processed by an external process.
	 * <p>
	 * Single time-points will be resaved as ImageJ TIFFs, in the specified
	 * folder, with a name ending with the time-point value (0-based). Examples:
	 * "0.tif", "20.tif".
	 *
	 * @param img
	 *            the image to save.
	 * @param interval
	 *            the interval that specifies how to crop the image before
	 *            saving. Can include time, X, Y, channels etc. Dimensions and
	 *            dimensions order must much that of the image.
	 * @param folder
	 *            the folder in which to save
	 * @param logger
	 *            a logger to report progress.
	 * @param <T>
	 *            the pixel type in the input image.
	 * @return <code>true</code> if resaving happened without issues.
	 */
	public static < T extends RealType< T > & NativeType< T > > boolean resaveSingleTimePoints(
			final ImgPlus< T > img,
			final Interval interval,
			final String folder,
			final Logger logger )
	{
		final int zIndex = img.dimensionIndex( Axes.Z );
		final int cIndex = img.dimensionIndex( Axes.CHANNEL );
		final Interval cropInterval;
		if ( zIndex < 0 )
		{
			// 2D
			if ( cIndex < 0 )
				cropInterval = Intervals.createMinMax(
						interval.min( 0 ), interval.min( 1 ),
						interval.max( 0 ), interval.max( 1 ) );
			else
				// Include all channels
				cropInterval = Intervals.createMinMax(
						interval.min( 0 ), interval.min( 1 ), img.min( cIndex ),
						interval.max( 0 ), interval.max( 1 ), img.max( cIndex ) );
		}
		else
		{
			if ( cIndex < 0 )
				cropInterval = Intervals.createMinMax(
						interval.min( 0 ), interval.min( 1 ), interval.min( 2 ),
						interval.max( 0 ), interval.max( 1 ), interval.max( 2 ) );
			else
				cropInterval = Intervals.createMinMax(
						interval.min( 0 ), interval.min( 1 ), interval.min( 2 ), img.min( cIndex ),
						interval.max( 0 ), interval.max( 1 ), interval.max( 2 ), img.max( cIndex ) );
		}

		final int timeIndex = img.dimensionIndex( Axes.TIME );
		if ( timeIndex < 0 )
		{
			// No time, just save the image.
			final IntervalView< T > crop = Views.interval( img, cropInterval );
			final String name = nameGen.apply( 0l );
			final ImagePlus imp = ImageJFunctions.wrap( crop, name );
			final String path = folder + File.separator + name + ".tif";
			return IJ.saveAsTiff( convertToRGB( imp ), path );
		}
		else
		{
			// In the interval, time is always the last.
			final long minT = interval.min( interval.numDimensions() - 1 );
			final long maxT = interval.max( interval.numDimensions() - 1 );
			for ( long t = minT; t <= maxT; t++ )
			{
				final ImgPlus< T > tpTCZ = ImgPlusViews.hyperSlice( img, timeIndex, t );

				// Put if necessary the channel axis as the last one (CellPose
				// format)
				final int chanDim = tpTCZ.dimensionIndex( Axes.CHANNEL );
				ImgPlus< T > tp = tpTCZ;
				if ( chanDim > 1 )
					tp = ImgPlusViews.moveAxis( tpTCZ, chanDim, tpTCZ.numDimensions() - 1 );

				// possibly 2D or 3D with or without channel.
				final IntervalView< T > crop = Views.interval( tp, cropInterval );
				final String name = nameGen.apply( t );
				final String path = folder + File.separator + name + ".tif";
				final ImagePlus imp = ImageJFunctions.wrap( crop, name );
				final boolean ok = IJ.saveAsTiff( convertToRGB( imp ), path );
				if ( !ok )
					return false;

				logger.setProgress( ( double ) ( t + 1 - minT ) / ( maxT - minT + 1 ) );
			}
			return true;
		}
	}

	private static ImagePlus convertToRGB( final ImagePlus imp )
	{
		final ImageProcessor ip = imp.getProcessor();
		if ( ip instanceof ColorProcessor )
		{ return imp; }

		final ImageProcessor rgbProcessor = ip.convertToRGB();
		final ImagePlus rgbImage = new ImagePlus( imp.getTitle() + "-RGB", rgbProcessor );
		return rgbImage;
	}


	public static final Function< Long, String > nameGen = ( frame ) -> String.format( "%d", frame );


	/**
	 * A tailer listener that parse YOLO log to fetch when an image has been
	 * processed, and increase the progress counter.
	 */
	public static class YOLOTailerListener extends TailerListenerAdapter
	{
		private final Logger logger;

		private final int nTodos;

		private int nDone;

		private final static Pattern IMAGE_NUMBER_PATTERN = Pattern.compile( "^image \\d+/\\d+.*" );

		public YOLOTailerListener( final Logger logger, final int nTodos )
		{
			this.logger = logger;
			this.nTodos = nTodos;
			this.nDone = 0;
		}

		@Override
		public void handle( final String line )
		{
			final Matcher matcher = IMAGE_NUMBER_PATTERN.matcher( line );

			if ( matcher.matches() )
			{
				// Simply increment the 'done' counter.
				nDone++;
				logger.setProgress( ( double ) nDone / nTodos );
			}
			else
			{
				if ( !line.trim().isEmpty() )
					logger.log( " - " + line + '\n' );
			}
		}
	}

	/**
	 * Import the text results files generated by the 'save_txt' option, and
	 * returns them as a list of spots. The radius of the spots is the mean of
	 * the width and height of the YOLO detections.
	 * <p>
	 * The YOLO results text file is made of one line per detection, and each
	 * line is formatted as follow:
	 * <p>
	 * <code>
	 * class_id center_x center_y width height confidence
	 * </code>
	 * <p>
	 * Where:
	 * <ul>
	 * <li>class_id is the class identifier of the detected object
	 * <li>center_x and center_y are the normalized coordinates of the center of
	 * the bounding box (values between 0 and 1)
	 * <li>width and height are the normalized width and height of the bounding
	 * box (values between 0 and 1).
	 * <li>(optional) confidence is the confidence score of the detection
	 * </ul>
	 *
	 * @param path
	 *            the path to the YOLO results file.
	 * @param interval
	 *            the interval in the input image that was passed to YOLO.
	 * @param calibration
	 *            the physical calibration of the input image.
	 * @param logger
	 *            a {@link Logger} to report error messages.
	 * @return a new list of spots.
	 */
	public static List< Spot > importResultFile(
			final String path,
			final Interval interval,
			final double[] calibration,
			final Logger logger )
	{
		final long width = interval.dimension( 0 );
		final long height = interval.dimension( 1 );
		final long x0 = interval.min( 0 );
		final long y0 = interval.min( 1 );

		final List< Spot > spots = new ArrayList<>();
		try (BufferedReader br = new BufferedReader( new FileReader( path ) ))
		{
			String line;
			int ln = 0;
			while ( ( line = br.readLine() ) != null )
			{
				ln++;
				final String[] values = line.split( " " );
				if ( values.length < 5 )
				{
					logger.error( "Line " + ln + " in file " + path + " as unexpected number of values. Should be at least 5, but was " + values.length + "." );
					continue;
				}
				// Center
				final double xr = Double.parseDouble( values[ 1 ].trim() );
				final double yr = Double.parseDouble( values[ 2 ].trim() );
				// Size
				final double wr = Double.parseDouble( values[ 3 ].trim() );
				final double hr = Double.parseDouble( values[ 4 ].trim() );

				// Global coords
				final double x = calibration[ 0 ] * ( x0 + xr * width );
				final double y = calibration[ 1 ] * ( y0 + yr * height );
				final double w = calibration[ 0 ] * wr * width;
				final double h = calibration[ 1 ] * hr * height;
				final double r = 0.5 * ( w + h ) / 2.;

				// Do we have confidence?
				final double quality;
				if ( values.length >= 5 )
					quality = Double.parseDouble( values[ 5 ].trim() );
				else
					quality = 1.;

				final Spot spot = new Spot( x, y, 0., r, quality );
				spots.add( spot );
			}
		}
		catch ( final IOException e )
		{
			logger.error( "Error reading the file " + path + "\n" + e.getMessage() + '\n' );
			e.printStackTrace();
			return Collections.emptyList();
		}

		return spots;
	}
}
