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
