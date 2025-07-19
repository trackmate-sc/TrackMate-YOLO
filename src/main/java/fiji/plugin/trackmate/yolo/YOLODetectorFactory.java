/*-
 * #%L
 * TrackMate: your buddy for everyday tracking.
 * %%
 * Copyright (C) 2021 - 2023 TrackMate developers.
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

import java.net.URL;
import java.util.Map;

import javax.swing.ImageIcon;

import org.scijava.Priority;
import org.scijava.plugin.Plugin;

import fiji.plugin.trackmate.detection.SpotDetectorFactory;
import fiji.plugin.trackmate.detection.SpotDetectorFactoryGenericConfig;
import fiji.plugin.trackmate.detection.SpotGlobalDetector;
import fiji.plugin.trackmate.detection.SpotGlobalDetectorFactory;
import fiji.plugin.trackmate.gui.GuiUtils;
import fiji.plugin.trackmate.util.cli.TrackMateSettingsBuilder;
import ij.ImagePlus;
import net.imagej.ImgPlus;
import net.imglib2.Interval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

@Plugin( type = SpotDetectorFactory.class, priority = Priority.LOW )
public class YOLODetectorFactory< T extends RealType< T > & NativeType< T > > implements SpotGlobalDetectorFactory< T >, SpotDetectorFactoryGenericConfig< T, YOLOCLI >
{

	/**
	 * The key to the parameter that stores the path to the custom model file to
	 * use with YOLO. It must be an absolute file path.
	 */
	public static final String KEY_YOLO_MODEL_FILEPATH = "YOLO_MODEL_FILEPATH";

	public static final String DEFAULT_YOLO_MODEL_FILEPATH = "";

	/**
	 * Key for the parameters that sets the minimum confidence threshold for
	 * detections.
	 */
	public static final String KEY_YOLO_CONF = "YOLO_CONF_THRESHOLD";

	public static final double DEFAULT_YOLO_CONF = 0.25;

	/**
	 * Key for the parameters that sets the IoU threshold for Non-Maximum
	 * Suppression.
	 */
	public static final String KEY_YOLO_IOU = "YOLO_IOU_THRESHOLD";

	public static final double DEFAULT_YOLO_IOU = 0.7;

	public static final String KEY_USE_GPU = "USE_GPU";

	public static final boolean DEFAULT_USE_GPU = true;

	/** A string key identifying this factory. */
	public static final String DETECTOR_KEY = "YOLO_DETECTOR";

	/** The pretty name of the target detector. */
	public static final String NAME = "YOLO detector";

	public static final ImageIcon ICON;
	static
	{
		final URL resource = GuiUtils.getResource( "images/YOLO-logo-64px.png", YOLODetectorFactory.class );
		ICON = new ImageIcon( resource );
	}

	public static final String DOC_YOLO_URL = "https://imagej.net/plugins/trackmate/detectors/trackmate-yolo";

	/** An html information text. */
	public static final String INFO_TEXT = "<html>"
			+ "This detector relies on YOLO to detect objects."
			+ "<p>"
			+ "The detector simply calls an external YOLO installation. So for this "
			+ "to work, you must have a YOLO installation running on your computer. "
			+ "<p>"
			+ "If you use this detector for your work, please be so kind as to "
			+ "also cite the YOLO github repo: <a href=\"https://github.com/ultralytics/ultralytics\">"
			+ "Jocher, G., Qiu, J., & Chaurasia, A. (2023). "
			+ "Ultralytics YOLO. https://github.com/ultralytics/ultralytics</a>"
			+ "<p>"
			+ "Documentation for this module "
			+ "<a href=\"" + DOC_YOLO_URL + "\">on the ImageJ Wiki</a>."
			+ "</html>";

	@Override
	public YOLOCLI getConfigurator( final ImagePlus imp )
	{
		return new YOLOCLI();
	}

	@Override
	public SpotGlobalDetector< T > getDetector( final ImgPlus< T > img, final Map< String, Object > settings, final Interval interval )
	{
		final YOLOCLI cli = getConfigurator( img );
		TrackMateSettingsBuilder.fromTrackMateSettings( settings, cli );
		final YOLODetector< T > detector = new YOLODetector<>(
				img,
				interval,
				cli );
		return detector;
	}

	@Override
	public boolean forbidMultithreading()
	{
		return true;
	}

	@Override
	public boolean has2Dsegmentation()
	{
		return false;
	}

	@Override
	public String getInfoText()
	{
		return INFO_TEXT;
	}

	@Override
	public ImageIcon getIcon()
	{
		return ICON;
	}

	@Override
	public String getKey()
	{
		return DETECTOR_KEY;
	}

	@Override
	public String getName()
	{
		return NAME;
	}

	@Override
	public String getUrl()
	{
		return DOC_YOLO_URL;
	}
}
