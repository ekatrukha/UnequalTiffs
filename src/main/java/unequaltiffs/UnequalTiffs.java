package unequaltiffs;

import java.awt.Font;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.gui.Overlay;
import ij.gui.TextRoi;
import ij.gui.Toolbar;
import ij.io.DirectoryChooser;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import io.scif.config.SCIFIOConfig;
import io.scif.config.SCIFIOConfig.ImgMode;
import io.scif.img.ImgIOException;
import io.scif.img.ImgOpener;
import net.imglib2.Cursor;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class UnequalTiffs < T extends RealType< T > & NativeType< T > > implements PlugIn {

	final String sPluginVersion = "0.0.1";
	int nChannels;
	int nSlices;
	int nTimePoints;
	boolean bMultiCh = false;
	ArrayList<long []> im_dims;
	ArrayList<Img<T>> imgs_in;
	String [] sFileNamesShort;
	int nDimN;
	int nImgN;
	Calibration cal;
	int [] ipDim;
	public static final int ALIGN_ZERO=0, ALIGN_CENTER=1;
	int nAlignMontage;
	String sFileExtension = ".tif";
	
	@Override
	public void run(String arg) {

		if(arg == null)
			return;
		
		DirectoryChooser dc = new DirectoryChooser ( "Choose a folder with TIFF images.." );
		String sPath = dc.getDirectory();
		if(!initializeAndCheckConsistensy(sPath))
			return;
		nImgN = imgs_in.size();
		
		//making montage
		if(arg.equals("Montage"))
		{
			
			//Calculate the number of rows and columns
			//Based on MontageMaker, this tries to make the 
			//montage as square as possible

			int nCols = (int)Math.sqrt(nImgN);
			int nRows = nCols;
			int n = nImgN - nCols*nRows;
			if (n>0) nCols += (int)Math.ceil((double)n/nRows);
			final String[] sAlingMontage = new String[2];
			sAlingMontage[0] = "Zero (origin)";
			sAlingMontage[1] = "Center";
			
			final GenericDialog gdMontage = new GenericDialog( "Make montage" );
			gdMontage.addNumericField("number of columns:", nCols, 0);
			gdMontage.addNumericField("number of rows:", nRows, 0);
			gdMontage.addChoice( "Align images by:", sAlingMontage, Prefs.get("UnequalTiffs.nAlignMontage", sAlingMontage[ALIGN_ZERO]) );
			gdMontage.showDialog();
			if (gdMontage.wasCanceled() )
				return;
			nCols = (int) gdMontage.getNextNumber();
			nRows = (int) gdMontage.getNextNumber();
			nAlignMontage = gdMontage.getNextChoiceIndex();
			Prefs.set("UnequalTiffs.nAlignMontage", sAlingMontage[nAlignMontage]);
			n = nImgN - nCols*nRows;
			if (n>0) nCols += (int)Math.ceil((double)n/nRows);
			IJ.log("Making montage with "+Integer.toString(nRows)+" rows and "+Integer.toString(nCols)+" columns.");

			UTMontage<T> utM = new UTMontage<T>(imgs_in, im_dims, bMultiCh);
			Img<T> img_montage = utM.makeMontage(nRows, nCols, nAlignMontage);
			ImagePlus ip_montage;
			
			ip_montage = ImageJFunctions.show(prepareMontageForImageJView(img_montage), "Montage" );
			ip_montage.setCalibration(cal);
			utM.addCaptionsOverlay(sFileNamesShort, ip_montage);


			IJ.log("Done.");
		}
		
		if(arg.equals("Concatenate"))
		{
			IJ.log("Not implemented yet.");
		}
	}
	
	/** function reads all tiffs from the sPath and ensures they have the same
	 * dimensions and number of channels.
	 * If succeeded, fills all member variables of this class **/
	@SuppressWarnings("unchecked")
	boolean initializeAndCheckConsistensy(String sPath)
	{
		// getting list of files
		List<String> filenames = null;
		if (sPath != null)
		{
			IJ.log("UnequalTiffCombineMontage v."+sPluginVersion);
			IJ.log("Analyzing folder "+sPath);
			try {
				filenames = getFilenamesFromFolder(sPath, sFileExtension);
			} catch (IOException e) {
				e.printStackTrace();
				IJ.log(e.getMessage());
			}	
			if(filenames == null)
			{
				return false;
			}
			else
			if(filenames.isEmpty())
			{
				IJ.log("Cannot find any TIFF files in provided folder, aborting.");
				return false;
			}
			else
			{
				IJ.log("Found " +Integer.toString(filenames.size())+" TIFF files.");
			}
		}
		else
			return false;
		
		fillFilenamesArray(filenames);
		IJ.log("Analyzing dimensions:");
		
		ImagePlus ipFirst = IJ.openImage(filenames.get(0));
		cal = ipFirst.getCalibration();
		ipDim = ipFirst.getDimensions();
		String sDims = "XY";
		nChannels = ipFirst.getNChannels();
		nSlices = ipFirst.getNSlices();
		nTimePoints = ipFirst.getNFrames();
		if(nChannels>1)
		{
			bMultiCh = true;
			sDims = sDims + "C";
		}
		if(nSlices>1)
		{
			sDims = sDims + "Z";
		}
		if(nTimePoints>1)
		{
			sDims = sDims + "T";
		}
		sDims = sDims +" and " + Integer.toString(ipFirst.getBitDepth())+"-bit";
		ipFirst.close();
		
		if(bMultiCh)
		{
			sDims = sDims +" with "+ Integer.toString(nChannels)+" channels";
		}
		IJ.log(" - Inferring general dimensions/pixel sizes from "+filenames.get(0));
		IJ.log(" - Assuming all files are "+sDims+" ");

		
		ImgOpener imgOpener = new ImgOpener();
		SCIFIOConfig config = new SCIFIOConfig();
		config.imgOpenerSetImgModes( ImgMode.CELL );
		Img< ? > firstImg =  imgOpener.openImgs( filenames.get(0), config ).get( 0 );
		Cursor<?> cursorTest = firstImg.cursor();
		cursorTest.fwd();
		@SuppressWarnings("rawtypes")
		Class TypeIni = cursorTest.get().getClass();
		nDimN = firstImg.numDimensions();
		im_dims = new ArrayList<long[]>();
		Img< ? > imageCell;
		imgs_in = new ArrayList<Img<T>>();
		imgs_in.add((Img<T>) firstImg);
		im_dims.add(firstImg.dimensionsAsLongArray());
		for(int i=1;i<filenames.size();i++)
		{
			imageCell = imgOpener.openImgs( filenames.get(i), config ).get( 0 );
			//check the bit depth
			cursorTest = imageCell.cursor();
			cursorTest.fwd();
			if(!TypeIni.isInstance(cursorTest.get()))
			{
				IJ.log("ERROR! File "+filenames.get(i)+"\nhas different bit depth, aborting.");
				return false;
			}
			//simple check of dimension number
			if(imageCell.numDimensions()!=nDimN)
			{
				IJ.log("ERROR! File "+filenames.get(i)+"\nhas different dimensions, aborting.");				
				return false;
			}
			//IJ.log(Integer.toString(imageCell.numDimensions()));
			im_dims.add(imageCell.dimensionsAsLongArray());
			if(bMultiCh)
			{
				if(im_dims.get(i)[2]!=nChannels)
				{
					IJ.log("ERROR! File "+filenames.get(i)+"\nhas different number of channels, aborting.");				
					return false ;
				}
			}
			imgs_in.add((Img<T>) imageCell);

		}

		return true;
	}


	/**given the path to folder and file extension, returns List<String> of filenames **/
	public static List<String> getFilenamesFromFolder(final String sFolderPath, final String fileExtension)    
			throws IOException {
		final Path path = Paths.get(sFolderPath);

		if (!Files.isDirectory(path)) {
			throw new IllegalArgumentException("Path must be a directory!");
		}

		List<String> result = null;

		try (Stream<Path> stream = Files.list(path)) {
			result = stream
					.filter(p -> !Files.isDirectory(p))
					// this is a path, not string,
					// this only test if path end with a certain path
					//.filter(p -> p.endsWith(fileExtension))
					// convert path to string first
					.map(p -> p.toString())
					.filter(f -> f.endsWith(fileExtension))
					.collect(Collectors.toList());
		} catch (IOException e) {
			e.printStackTrace();
		}

		return result;
	}
	
	public void fillFilenamesArray(List<String> fullPath)
	{
		sFileNamesShort = new String[fullPath.size()];
		Path p;
		for(int i=0;i<fullPath.size();i++)
		{
			 p = Paths.get(fullPath.get(i));
			 sFileNamesShort[i] = p.getFileName().toString();
			 sFileNamesShort[i] = sFileNamesShort[i].substring(0, sFileNamesShort[i].length()-sFileExtension.length());
		}
		
	}
	
	/** function orders input Img<T> to ImageJ order of XYCZT **/	
	public IntervalView<T> prepareMontageForImageJView(final Img<T> img_montage)
	{
		IntervalView<T> out = Views.interval(img_montage, img_montage);
		if(!bMultiCh)
		{
			//so it looks a bit better
			out = Views.permute(Views.addDimension(out, 0, 0),2,out.numDimensions());
		}
		if(nTimePoints>1 && nSlices == 1)
		{
			out = Views.permute(Views.addDimension(out, 0, 0),out.numDimensions()-1,out.numDimensions());
		}

		return out;
	}
	
    
	public static void main( final String[] args ) throws ImgIOException, IncompatibleTypeException
	{
		// open an ImageJ window
		new ImageJ();
		UnequalTiffs un = new UnequalTiffs();
		un.run("Montage");
	
	}

}
