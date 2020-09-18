/*
 * Copyright 2019 PRImA Research Lab, University of Salford, United Kingdom
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.primaresearch.dla.page.converter;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

import org.primaresearch.dla.page.Page;
import org.primaresearch.dla.page.Page.MeasurementUnit;
import org.primaresearch.dla.page.io.FileInput;
import org.primaresearch.dla.page.io.FileTarget;
import org.primaresearch.dla.page.io.PageWriter;
import org.primaresearch.dla.page.io.json.GoogleJsonPageReader;
import org.primaresearch.dla.page.io.xml.PageXmlInputOutput;
import org.primaresearch.dla.page.io.xml.PageXmlModelAndValidatorProvider;
import org.primaresearch.dla.page.io.xml.XmlPageWriter_Alto;
import org.primaresearch.dla.page.layout.PageLayout;
import org.primaresearch.dla.page.layout.physical.ContentObject;
import org.primaresearch.dla.page.layout.physical.ContentObjectProcessor;
import org.primaresearch.dla.page.layout.physical.Region;
import org.primaresearch.dla.page.layout.physical.text.LowLevelTextContainer;
import org.primaresearch.dla.page.layout.physical.text.LowLevelTextObject;
import org.primaresearch.dla.page.layout.physical.text.TextObject;
import org.primaresearch.dla.page.layout.physical.text.impl.Glyph;
import org.primaresearch.dla.page.layout.physical.text.impl.TextLine;
import org.primaresearch.dla.page.layout.physical.text.impl.TextRegion;
import org.primaresearch.dla.page.layout.physical.text.impl.Word;
import org.primaresearch.dla.page.layout.shared.GeometricObject;
import org.primaresearch.io.FormatVersion;
import org.primaresearch.io.UnsupportedFormatVersionException;
import org.primaresearch.io.xml.IOError;
import org.primaresearch.io.xml.XmlFormatVersion;
import org.primaresearch.io.xml.XmlModelAndValidatorProvider;
import org.primaresearch.io.xml.XmlValidator;
import org.primaresearch.io.xml.variable.XmlVariableFileReader;
import org.primaresearch.maths.geometry.Point;
import org.primaresearch.maths.geometry.Polygon;
import org.primaresearch.shared.variable.DoubleValue;
import org.primaresearch.shared.variable.StringValue;
import org.primaresearch.shared.variable.VariableMap;
import org.primaresearch.text.filter.TextFilter;
import org.primaresearch.text.filter.TextFilter.TextObjectTypeFilterCallback;


/**
 * Converter tool for PAGE XML.
 * 
 * @author Christian Clausner
 *
 */
public class PageConverter {

	private static final String NEG_COORDS_MODE_REMOVE_OBJECT = "removeObj";
	//private static final String NEG_COORDS_MODE_TO_ZERO = "toZero";

	private String gtsidToSet = null;
	private FormatVersion targetformat = null;
	private VariableMap textFilterRules = null;
	private Double xResolution = null;
	private Double yResolution = null;
	private String resolutionUnit = null;
	private boolean transformCoords = false;
	
	/**
	 * Main function
	 * @param args Command line arguments - call with empty array to print usage help to stdout
	 */
	public static void main(String[] args) {
		if (args.length == 0) {
			showUsage();
			return;
		}

		PageConverter converter = new PageConverter();
		boolean alto = false;
		
		//Parse arguments
		String sourceFilename = null;
		boolean json = false;
		String targetFilename = null;
		String gtsidPattern = null;
		String textFilterRuleFile = null;
		String negCoordsMode = null;
		
		for (int i=0; i<args.length; i++) {
			if ("-source-xml".equals(args[i])) {
				i++;
				json = false;
				sourceFilename = args[i];
			}
			else if ("-source-json".equals(args[i])) {
				i++;
				json = true;
				sourceFilename = args[i];
			}
			else if ("-target-xml".equals(args[i])) {
				i++;
				targetFilename = args[i];
			}
			else if ("-set-gtsid".equals(args[i])) {
				i++;
				gtsidPattern = args[i];
			}
			else if ("-convert-to".equals(args[i])) {
				i++;
				if ("ALTO".equals(args[i]))
					alto = true;
				else
					converter.setTargetSchema(args[i]);
			}
			else if ("-text-filter".equals(args[i])) {
				i++;
				textFilterRuleFile = args[i];
			}
			else if ("-neg-coords".equals(args[i])) {
				i++;
				negCoordsMode = args[i];
			}
			else if ("-set-xres".equals(args[i])) {
				i++;
				converter.setxResolution(Double.parseDouble(args[i]));
			}
			else if ("-set-yres".equals(args[i])) {
				i++;
				converter.setyResolution(Double.parseDouble(args[i]));
			}
			else if ("-set-res".equals(args[i])) {
				i++;
				converter.setxResolution(Double.parseDouble(args[i]));
				converter.setyResolution(Double.parseDouble(args[i]));
			}
			else if ("-set-res-unit".equals(args[i])) {
				i++;
				converter.setResolutionUnit(args[i]);
			}
			else if ("-transform-coords".equals(args[i])) {
				converter.setTransformCoords(true);
			}
			else {
				System.err.println("Unknown argument: "+args[i]);
			}
		}
		
		//Set GtsID
		if (gtsidPattern != null)
			converter.setGtsId(gtsidPattern, sourceFilename);
		
		//Text filter
		if (textFilterRuleFile != null) {
			XmlVariableFileReader reader = new XmlVariableFileReader();
			try {
				converter.setTextFilterRules(reader.read(new File(textFilterRuleFile).toURI().toURL()));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		//Run conversion
		converter.run(sourceFilename, targetFilename, json, alto, negCoordsMode);
	}

	/**
	 * Print usage help to stdout
	 */
	private static void showUsage() {
		System.out.println("PAGE Converter");
		System.out.println("");
		System.out.println("PRImA Research Lab, University of Salford, UK");
		System.out.println("");
		System.out.println("Arguments:");
		System.out.println("");
		System.out.println("  -source-xml <XML file>        PAGE XML file to convert.");
		System.out.println("     OR");
		System.out.println("  -source-json <JSON file>      JSON file to convert (e.g. Google Cloud Vision output).");
		System.out.println("");
		System.out.println("  -target-xml <XML file>        Output PAGE XML file.");
		System.out.println("");
		System.out.println("  -convert-to <schema version>  Target PAGE schema version. (optional)");
		System.out.println("              Available versions:");
		System.out.println("                 LATEST");
		System.out.println("                 2018-07-15");
		System.out.println("                 2017-07-15");
		System.out.println("                 2016-07-15");
		System.out.println("                 2013-07-15");
		System.out.println("                 2010-03-19");
		System.out.println("                 ALTO");
		System.out.println("");
		System.out.println("  -set-gtsid <ID|prefix[start,end]>   To set the the GtsId field. (optional)");
		System.out.println("         Usage:");
		System.out.println("              Use <ID> to set a specific GtsId.");
		System.out.println("              Use <prefix[start,end]> to extract the GtsId from the");
		System.out.println("              filename of -source-xml.");
		System.out.println("         Examples:");
		System.out.println("              -set-gtsid 00236178     Given ID");
		System.out.println("              -set-gtsid pc-[0,7]     Prefix + first 8 characters of filename");
		System.out.println("");
		System.out.println("  -text-filter <XML file>   Applies filter to the text content. (optional)");
		System.out.println("         For instructions on how to define filter rules see the user guide.");
		System.out.println("");
		System.out.println("  -neg-coords <mode>   Handle negative coordinates (optional)");
		System.out.println("       Modes:");
		System.out.println("         removeObj - If an object contains one or more points with negative");
		System.out.println("                     coordinates, remove the whole object. ");
		System.out.println("         toZero    - Change negative values to 0");
		System.out.println("");
		System.out.println("  -set-xres <number>   To set x-resolution value of PAGE output. (optional)");
		System.out.println("  -set-yres <number>   To set y-resolution value of PAGE output. (optional)");
		System.out.println("  -set-res <number>    To set x- and y-resolution value of PAGE output. (optional)");
		System.out.println("  -set-res-unit <PPI|PPCM|other>    To set resolution unit of PAGE output. (optional)");
		System.out.println("  -transform-coords    Adjust all coords according to resolution");
		System.out.println("                       and measurement unit. (optional)");
	}
	
	/**
	 * Runs the conversion
	 * @param sourceFilename File path of input PAGE XML
	 * @param targetFilename File path to output PAGE XML
	 * @param json JSON input?
	 * @param altoOutput ALTO XML output instead of PAGE?
	 */
	public void run(String sourceFilename, String targetFilename, boolean json, boolean altoOutput, String negCoordsMode) {
		//Load
		Page page = null;
		try {
			if (json)
				page = new GoogleJsonPageReader().read(new FileInput(new File(sourceFilename)));
			else //XML
				page = PageXmlInputOutput.readPage(sourceFilename);
		} catch (Exception e) {
			System.err.println("Could not load source PAGE XML file: "+sourceFilename);
			e.printStackTrace();
			return;
		}
		
		//Set GtsId
		if (gtsidToSet != null && !gtsidToSet.isEmpty()) {
			try {
				page.setGtsId(gtsidToSet);
			} catch (Exception exc) {
				System.err.println("Could not set the GtsId");
				exc.printStackTrace();
			}
		}
		
		//Resolution
		try {
			if (xResolution != null)
				page.getAttributes().get("imageXResolution").setValue(new DoubleValue(xResolution.doubleValue()));
			if (yResolution != null)
				page.getAttributes().get("imageYResolution").setValue(new DoubleValue(yResolution.doubleValue()));
			if (resolutionUnit != null)
				page.getAttributes().get("imageResolutionUnit").setValue(new StringValue(resolutionUnit));
		} catch (Exception exc) {
			exc.printStackTrace();
		}
		
		//Text filter
		if (textFilterRules != null) {
			runTextFilter(textFilterRules, page);
		}
		
		//Handle negative coordinates?
		if (negCoordsMode != null) {
			handleNegativeCoordinates(page, negCoordsMode);
		}
		
		if (altoOutput) {
			//Write ALTO	
			try {
				XmlModelAndValidatorProvider validatorProvider = new PageXmlModelAndValidatorProvider();
				XmlValidator validator = null;
				if (validatorProvider != null) {
					validator = validatorProvider.getValidator(new XmlFormatVersion("http://www.loc.gov/standards/alto/ns-v4#"));
				}
	
				//Valid
				PageWriter writer = new XmlPageWriter_Alto(validator);
		
				try {
					if (!writer.write(page, new FileTarget(new File(targetFilename)))) {
						System.err.println("Error writing target ALTO XML file");
						List<IOError> errors = ((XmlPageWriter_Alto)writer).getErrors();
						if (errors != null)
							for (IOError error : errors) {
								System.err.println(error.getMessage());
							}
					}
				} catch (UnsupportedFormatVersionException e) {
					System.err.println("Could not save target ALTO XML file: "+targetFilename);
					e.printStackTrace();
				}
			} catch (Exception exc) {
				System.err.println("Could not initialise ALTO XML writer");
				exc.printStackTrace();
			}
		}
		else {
			//Convert to specified schema version
			if (targetformat != null) {
				try {
					//ConverterHub.convert(page, XmlInputOutput.getInstance().getFormatModel(targetformat)); 
					page.setFormatVersion(PageXmlInputOutput.getInstance().getFormatModel(targetformat));
				} catch(Exception exc) {
					System.err.println("Could not convert to target XML schema format.");
					exc.printStackTrace();
				}
			}
			
			if (transformCoords)
				transformCoordinates(page);
				
			//Write PAGE
			try {
				if (!PageXmlInputOutput.writePage(page, targetFilename))
					System.err.println("Error writing target PAGE XML file");
			} catch (Exception e) {
				System.err.println("Could not save target PAGE XML file: "+targetFilename);
				e.printStackTrace();
			}
		}
		
	}

	/**
	 * Applies a set of filter rules to all text elements of the given page.
	 * The type of the target object (region, line, word, glyph) can be
	 * specified per rule.
	 * 
	 * @param textFilterRules A collection of String variables, each containing a filter rule in the variable value. 
	 * @param page Page object with text elements to apply the filter to.
	 */
	public static void runTextFilter(VariableMap textFilterRules, Page page) {
		final TextFilter textFilter = new TextFilter(textFilterRules);
		final ContentObjectProcessor processor = new ContentObjectProcessor() {
			@Override
			public void doProcess(ContentObject contentObject) {
				if (contentObject != null) {
					if (contentObject instanceof TextObject) {
						TextObject textObj = (TextObject)contentObject;
						String text = textObj.getText(); 
						if (text != null)
							textObj.setText(textFilter.filter(text));
					}
				}					
			}
		};
		
		//Callback for allowed text object types
		textFilter.setTextObjectTypeFilterCallback(new TextObjectTypeFilterCallback() {
			
			@Override
			public boolean textFilterEnabledForObjectType(String textObjectTypeFilter) {
				if (textObjectTypeFilter == null || textObjectTypeFilter.isEmpty())
					return true;
				ContentObject currentObject = processor.getCurrentObject();
				if (currentObject == null)
					return true;
				if (currentObject instanceof TextRegion) {
					return textObjectTypeFilter.toLowerCase().contains("r"); //Region
				}
				if (currentObject instanceof TextLine) {
					return textObjectTypeFilter.toLowerCase().contains("l"); //Text line
				}
				if (currentObject instanceof Word) {
					return textObjectTypeFilter.toLowerCase().contains("w"); //Word
				}
				if (currentObject instanceof Glyph) {
					return textObjectTypeFilter.toLowerCase().contains("g"); //Glyph
				}
				return false;
			}
		});
		
		//Run filter process
		try {
			processor.run(page);
		} catch(Exception exc) {
			System.out.println("Error while applying text filter.");
			exc.printStackTrace();
		}
	}
	
	/**
	 * Sets the GtsId that is to be added to the PAGE document.<br>
	 * Note: The ID has to be conform to the XML ID convention (start with letter, ...).
	 * @param pattern A specific ID or [start,end], where 'start' is the index of the first character
	 * and 'end' the index of the last character within the given filename (index starts with 0).
	 * @param filepath Required if the ID is to be extracted.
	 */
	public void setGtsId(String pattern, String filepath) {
		try {
			if (pattern.contains("[") && pattern.endsWith("]")) {
				int p = pattern.indexOf('[');
				String positionPattern =  pattern.substring(p+1, pattern.length()-1);
				String prefix = "";
				if (!pattern.startsWith("["))
					prefix = pattern.substring(0,p);
				String[] positions = positionPattern.split(",");
				if (positions != null && positions.length == 2) {
					int start = Integer.parseInt(positions[0]);
					int end = Integer.parseInt(positions[1]);
					String filename = extractFilename(filepath);
					gtsidToSet = prefix+filename.substring(start, end + 1);
				}
			} else {
				gtsidToSet = pattern;
			}
		} catch (Exception exc) {
			System.err.println("Could not extract GtsId from filename.");
			exc.printStackTrace();
		}
	}
	
	/**
	 * Sets the target PAGE XML schema version of the output file. 
	 * @param versionString LATEST, 2013-07-15 or 2010-03-19
	 */
	public void setTargetSchema(String versionString) {
		if ("LATEST".equals(versionString))
			targetformat = PageXmlInputOutput.getLatestSchemaModel().getVersion();
		else
			targetformat = new XmlFormatVersion(versionString);
	}
	
	/**
	 * Extracts the filename from a full file path. 
	 * @param filepath E.g. c:\temp\test.xml
	 * @return The filename (e.g. test.xml).
	 */
	private String extractFilename(String filepath) {
		if (!filepath.contains(File.separator))
			return filepath;
		return filepath.substring(filepath.lastIndexOf(File.separator)+1);
	}

	/**
	 * Sets the filter rules that are to be applied to all text regions.
	 * @param textFilterRules A collection of String variables, each containing a filter rule in the variable value.
	 */
	public void setTextFilterRules(VariableMap textFilterRules) {
		this.textFilterRules = textFilterRules;
	}
	
	/** Handle negative coordinates of any object with polygon. */
	public static void handleNegativeCoordinates(Page page, String negCoordsMode) {
		boolean deleteObjects = NEG_COORDS_MODE_REMOVE_OBJECT.equals(negCoordsMode);
		PageLayout layout = page.getLayout();
		
		//Printspace
		if (layout.getPrintSpace() != null && hasNegativeCoordinates(layout.getPrintSpace().getCoords())) {
			if (deleteObjects)
				layout.setPrintSpace(null);
			else
				correctNegativeCoordinates(layout.getPrintSpace().getCoords());
		}
		//Border
		if (layout.getBorder() != null && hasNegativeCoordinates(layout.getBorder().getCoords())) {
			if (deleteObjects)
				layout.setBorder(null);
			else
				correctNegativeCoordinates(layout.getBorder().getCoords());
		}
		//Regions
		List<Region> toDelete = new LinkedList<Region>();
		for (int i=0; i<layout.getRegionCount(); i++) {
			Region region = layout.getRegion(i);
			if (hasNegativeCoordinates(region.getCoords())) {
				if (deleteObjects)
					toDelete.add(region);
				else
					correctNegativeCoordinates(region.getCoords());
			}
			handleNegativeCoordinatesOfNestedRegions(region, deleteObjects);
			if (region instanceof TextRegion)
				handleNegativeCoordinatesOfTextObjects((TextRegion)region, deleteObjects);
		}
		for (Region region : toDelete)
			layout.removeRegion(region.getId());
	}
	
	/** Handle negative coordinates of nested regions (recursive) */
	private static void handleNegativeCoordinatesOfNestedRegions(Region region, boolean deleteObjects) {
		List<Region> toDelete = new LinkedList<Region>();
		for (int i=0; i<region.getRegionCount(); i++) {
			Region child = region.getRegion(i);
			if (hasNegativeCoordinates(child.getCoords())) {
				if (deleteObjects)
					toDelete.add(child);
				else
					correctNegativeCoordinates(child.getCoords());
			}
			handleNegativeCoordinatesOfNestedRegions(child, deleteObjects);
			if (region instanceof TextRegion)
				handleNegativeCoordinatesOfTextObjects((TextRegion)region, deleteObjects);
		}
		for (Region child : toDelete)
			region.removeRegion(child);
	}
	
	/** Handle negative coordinates of child text objects (recursive) */
	private static void handleNegativeCoordinatesOfTextObjects(LowLevelTextContainer container, boolean deleteObjects) {
		List<LowLevelTextObject> toDelete = new LinkedList<LowLevelTextObject>();
		for (int i=0; i<container.getTextObjectCount(); i++) {
			LowLevelTextObject child = container.getTextObject(i);
			if (hasNegativeCoordinates(child.getCoords())) {
				if (deleteObjects)
					toDelete.add(child);
				else
					correctNegativeCoordinates(child.getCoords());
			}
			
			if (child instanceof LowLevelTextContainer)
				handleNegativeCoordinatesOfTextObjects((LowLevelTextContainer)child, deleteObjects);
		}
		for (LowLevelTextObject child : toDelete)
			container.removeTextObject(child.getId());
	}
	
	/** Returns true if at least one coordinate is negative */
	private static boolean hasNegativeCoordinates(Polygon polygon) {
		if (polygon == null)
			return false;
		
		for (int i=0; i<polygon.getSize(); i++) {
			if (polygon.getPoint(i).x < 0 || polygon.getPoint(i).y < 0)
				return true;
		}
		return false;
	}
	
	/** Changes negative coordinates to zero */
	private static void correctNegativeCoordinates(Polygon polygon) {
		if (polygon == null)
			return;
		
		for (int i=0; i<polygon.getSize(); i++) {
			if (polygon.getPoint(i).x < 0)
				polygon.getPoint(i).x = 0;
			if (polygon.getPoint(i).y < 0)
				polygon.getPoint(i).y = 0;
		}
	}

	public void setxResolution(Double xResolution) {
		this.xResolution = xResolution;
	}

	public void setyResolution(Double yResolution) {
		this.yResolution = yResolution;
	}

	public void setResolutionUnit(String resolutionUnit) {
		this.resolutionUnit = resolutionUnit;
	}

	/** Transforms coordinates to pixels, if necessary */
	private void transformCoordinates(Page page) {
		if (xResolution == null || yResolution == null || page.getMeasurementUnit() == null || page.getMeasurementUnit().equals(MeasurementUnit.PIXEL))
			return; //Can't transform or no need

		//Determine factor
		// Get image resolution in PPI
		double xres = xResolution;
		double yres = yResolution;
		if (resolutionUnit != null && "PPCM".equals(resolutionUnit)) {
			xres *= 2.54;
			yres *= 2.54;
		}
		
		//Pixel size in measurement unit
		double pixelWidth = 1.0;
		double pixelHeight = 1.0;
		if (page.getMeasurementUnit().equals(MeasurementUnit.INCH_BY_1200)) {
			pixelWidth = 1200.0 / xres;
			pixelHeight = 1200.0 / yres;
		}
		else if (page.getMeasurementUnit().equals(MeasurementUnit.MM_BY_10)) {
			pixelWidth = 10.0 * 25.4 / xres;
			pixelHeight = 10.0 * 25.4 / yres;
		}
		
		scaleCoords(page, 1.0 / pixelWidth, 1.0 / pixelHeight);		
	}
	
	private void scaleCoords(Page page, double xFactor, double yFactor) {
		//Page size
		page.getLayout().setSize((int)(page.getLayout().getWidth() * xFactor), (int)(page.getLayout().getHeight() * yFactor));
		//Border, print space
		scaleCoords(page.getLayout().getBorder(), xFactor, yFactor);
		scaleCoords(page.getLayout().getPrintSpace(), xFactor, yFactor);
		//Regions
		for (int i=0; i<page.getLayout().getRegionCount(); i++)
			scaleRegionCoords(page.getLayout().getRegion(i), xFactor, yFactor);
	}
	
	private void scaleCoords(GeometricObject obj, double xFactor, double yFactor) {
		if (obj == null)
			return;
		
		scaleCoords(obj.getCoords(), xFactor, yFactor);
	}
	
	private void scaleCoords(Polygon polygon, double xFactor, double yFactor) {
		if (polygon == null)
			return;
		
		Point p;
		for (int i=0; i<polygon.getSize(); i++) {
			p = polygon.getPoint(i);
			p.x = (int)(p.x * xFactor);
			p.y = (int)(p.y * xFactor);
		}		
	}
	
	private void scaleRegionCoords(Region region, double xFactor, double yFactor) {
		//Self
		scaleCoords(region, xFactor, yFactor);
		
		//Nested regions
		for (int i=0; i<region.getRegionCount(); i++)
			scaleRegionCoords(region.getRegion(i), xFactor, yFactor);
		
		//Text lines
		if (region instanceof TextRegion) {
			LowLevelTextContainer container = (LowLevelTextContainer)region;
			for (int i=0; i<container.getTextObjectCount(); i++) {
				TextLine textLine = (TextLine)container.getTextObject(i);
				scaleLowLevelTextObject(textLine, xFactor, yFactor);
				//Baseline
				scaleCoords(textLine.getBaseline(), xFactor, yFactor);
			}
		}
	}
	
	private void scaleLowLevelTextObject(LowLevelTextObject obj, double xFactor, double yFactor) {
		//Self
		scaleCoords(obj, xFactor, yFactor);
		
		//Children
		if (obj instanceof LowLevelTextContainer) {
			LowLevelTextContainer container = (LowLevelTextContainer)obj;
			for (int i=0; i<container.getTextObjectCount(); i++)
				scaleLowLevelTextObject(container.getTextObject(i), xFactor, yFactor);
		}
	}

	public void setTransformCoords(boolean transformCoords) {
		this.transformCoords = transformCoords;
	}
	
}
