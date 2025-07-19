/*-
 * #%L
 * TrackMate: your buddy for everyday tracking.
 * %%
 * Copyright (C) 2024 TrackMate developers.
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

import static fiji.plugin.trackmate.yolo.YOLODetectorFactory.DEFAULT_USE_GPU;
import static fiji.plugin.trackmate.yolo.YOLODetectorFactory.DEFAULT_YOLO_CONF;
import static fiji.plugin.trackmate.yolo.YOLODetectorFactory.DEFAULT_YOLO_IOU;
import static fiji.plugin.trackmate.yolo.YOLODetectorFactory.DEFAULT_YOLO_MODEL_FILEPATH;
import static fiji.plugin.trackmate.yolo.YOLODetectorFactory.KEY_USE_GPU;
import static fiji.plugin.trackmate.yolo.YOLODetectorFactory.KEY_YOLO_CONF;
import static fiji.plugin.trackmate.yolo.YOLODetectorFactory.KEY_YOLO_IOU;
import static fiji.plugin.trackmate.yolo.YOLODetectorFactory.KEY_YOLO_MODEL_FILEPATH;

import java.util.Collections;

import fiji.plugin.trackmate.util.cli.CondaExecutableCLIConfigurator;
import ij.IJ;

public class YOLOCLI extends CondaExecutableCLIConfigurator
{

	private final PathArgument modelPath;

	private final PathArgument imageFolder;

	private final PathArgument outputFolder;

	private final DoubleArgument iou;

	private final DoubleArgument conf;

	private final Flag useGPU;

	public YOLOCLI()
	{
		this.modelPath = addPathArgument()
				.name( "Path to a YOLO model" )
				.argument( "model=" )
				.help( "The path to a YOLO model." )
				.defaultValue( DEFAULT_YOLO_MODEL_FILEPATH )
				.key( KEY_YOLO_MODEL_FILEPATH )
				.get();

		this.conf = addDoubleArgument()
				.name( "Confidence threshold" )
				.argument( "conf=" )
				.defaultValue( DEFAULT_YOLO_CONF )
				.min( 0. )
				.max( 1. )
				.help( "Sets the minimum confidence threshold for detections. Objects detected "
						+ "with confidence below this threshold will be disregarded. Adjusting "
						+ "this value can help reduce false positives." )
				.key( KEY_YOLO_CONF )
				.get();

		this.iou = addDoubleArgument()
				.name( "IoU threshold" )
				.argument( "iou=" )
				.defaultValue( DEFAULT_YOLO_IOU )
				.min( 0. )
				.max( 1. )
				.help( "Intersection Over Union (IoU) threshold for Non-Maximum Suppression (NMS). "
						+ "Lower values result in fewer detections by eliminating overlapping boxes, "
						+ "useful for reducing duplicates." )
				.key( KEY_YOLO_IOU )
				.get();

		this.imageFolder = addPathArgument()
				.name( "Input image folder path" )
				.help( "Directory with series of .tif files." )
				.argument( "source=" )
				.visible( false )
				.required( true )
				.key( null )
				.get();

		this.outputFolder = addPathArgument()
				.name( "Output folder" )
				.help( "Path to write the text files containing the detection boxes." )
				.argument( "project=" )
				.visible( false )
				.required( true )
				.key( null )
				.get();

		this.useGPU = addFlag()
				.name( "Use GPU" )
				.help( "Whether to use the GPU for inference. If false, the CPU will be used." )
				.argument( "device=" )
				.key( KEY_USE_GPU )
				.defaultValue( DEFAULT_USE_GPU )
				.required( true )
				.visible( true )
				.get();
		useGPU.set( false );
		setCommandTranslator( useGPU, f -> {
			if ( ( boolean ) f )
			{
				if ( IJ.isMacintosh() )
					return Collections.singletonList( "mps" );
				return Collections.singletonList( "cuda" );
			}
			else
			{
				return Collections.singletonList( "cpu" );
			}
		} );

		addFlag()
				.name( "Save detections to text files" )
				.help( "Whether the detections will be saved as a text files." )
				.argument( "save_txt=" )
				.defaultValue( true )
				.required( true )
				.visible( false )
				.key( null )
				.get();

		addFlag()
				.name( "Save confidence with detection" )
				.help( "Whether results text files will include the confidence values." )
				.argument( "save_conf=" )
				.defaultValue( true )
				.required( true )
				.visible( false )
				.key( null )
				.get();

		addFlag()
				.name( "Save detections overlay images" )
				.help( "Whether the results will be saved as an image overlaid with results." )
				.argument( "save=" )
				.defaultValue( false ) // We don't want that.
				.required( true )
				.visible( false )
				.key( null )
				.get();
	}

	@Override
	protected String getCommand()
	{
		return "yolo detect predict";
	}

	public PathArgument modelPath()
	{
		return modelPath;
	}

	public PathArgument imageFolder()
	{
		return imageFolder;
	}

	public PathArgument outputFolder()
	{
		return outputFolder;
	}

	public DoubleArgument iouThreshold()
	{
		return iou;
	}

	public DoubleArgument confidenceThreshold()
	{
		return conf;
	}
}
