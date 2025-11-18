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

import fiji.plugin.trackmate.Logger;
import fiji.plugin.trackmate.util.TMUtils;
import ij.IJ;
import ij.ImagePlus;
import net.imagej.ImgPlus;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

public class YOLOPlaygroundEntry
{

	public static < T extends RealType< T > & NativeType< T > > void run()
	{
		final String path = "samples/SHicham_Video1_crop.tif";
		final ImagePlus imp = IJ.openImage( path );
		@SuppressWarnings( "unchecked" )
		final ImgPlus< T > img = TMUtils.rawWraps( imp );

		final YOLOCLI cli = new YOLOCLI();
		cli.getCommandArg().set( "yolo" );
		cli.modelPath().set( "samples/best 8.pt" );

		final YOLODetector< T > detector = new YOLODetector< T >( img, img, cli );
		detector.setLogger( Logger.DEFAULT_LOGGER );
		final boolean ok = detector.checkInput() && detector.process();
		if ( !ok )
			System.err.println( detector.getErrorMessage() );
		else
			System.out.println( String.format( "Finished in %.1f s.", detector.getProcessingTime() / 1000. ) );
		System.out.println( detector.getResult() );
	}
}
