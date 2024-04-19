package unequaltiffs;

import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;


import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.io.DirectoryChooser;
import ij.plugin.PlugIn;

import io.scif.img.ImgIOException;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class UnequalTiffs < T extends RealType< T > & NativeType< T > > implements PlugIn, WindowListener {

	final String sPluginVersion = "0.0.2";


	int nImgN;
	int [] ipDim;
	public static final int ALIGN_ZERO=0, ALIGN_CENTER=1;
	int nAlignMontage;
	String sFileExtension = ".tif";
	UTImageSet<T> imageSet = new UTImageSet<T>();
	
	@Override
	public void run(String arg) {

		if(arg == null)
			return;
		
		DirectoryChooser dc = new DirectoryChooser ( "Choose a folder with TIFF images.." );
		String sPath = dc.getDirectory();
		if(!imageSet.initializeAndCheckConsistensy(sPath, sFileExtension))
			return;
		nImgN = imageSet.imgs_in.size();
		IJ.log("UnequalTiffCombineMontage v."+sPluginVersion);
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

			UTMontage<T> utM = new UTMontage<T>(imageSet);
			Img<T> img_montage = utM.makeMontage(nRows, nCols, nAlignMontage);
			ImagePlus ip_montage;
			
			ip_montage = ImageJFunctions.show(prepareMontageForImageJView(img_montage), "Montage" );
			ip_montage.setCalibration(imageSet.cal);
			utM.addCaptionsOverlay(imageSet.sFileNamesShort, ip_montage);
			ip_montage.getWindow().addWindowListener(this);

			IJ.log("Done.");
		}
		
		if(arg.equals("Concatenate"))
		{
			IJ.log("Not implemented yet.");
		}
		if(arg.equals("BrowseBDV"))
		{
			UTExploreBDV<T> exploreBDV = new UTExploreBDV<T>(imageSet);
			exploreBDV.browseBDV();
		}
		if(arg.equals("Explore"))
		{
			UTExploreBVV<T> exploreBVV = new UTExploreBVV<T>(imageSet);
			exploreBVV.browseBVV();
		}
	}
	
	
	/** function orders input Img<T> to ImageJ order of XYCZT **/	
	public IntervalView<T> prepareMontageForImageJView(final Img<T> img_montage)
	{
		IntervalView<T> out = Views.interval(img_montage, img_montage);
		if(!imageSet.bMultiCh)
		{
			//so it looks a bit better
			out = Views.permute(Views.addDimension(out, 0, 0),2,out.numDimensions());
		}
		if(imageSet.nTimePoints>1 && imageSet.nSlices == 1)
		{
			out = Views.permute(Views.addDimension(out, 0, 0),out.numDimensions()-1,out.numDimensions());
		}

		return out;
	}
	
    
	public static void main( final String[] args ) throws ImgIOException, IncompatibleTypeException
	{
		// open an ImageJ window
		new ImageJ();
		@SuppressWarnings("rawtypes")
		UnequalTiffs un = new UnequalTiffs();
		//un.run("Montage");		
		un.run("Explore");
	
	}

	@Override
	public void windowOpened(WindowEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void windowClosing(WindowEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void windowClosed(WindowEvent e) {
		// TODO Auto-generated method stub
		if(imageSet.filesOpened!=null)
		{
			for (int i=0;i<imageSet.filesOpened.size();i++)
			{
				imageSet.filesOpened.get(i).dispose();
			}
		}
		
	}

	@Override
	public void windowIconified(WindowEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void windowDeiconified(WindowEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void windowActivated(WindowEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void windowDeactivated(WindowEvent e) {
		// TODO Auto-generated method stub
		
	}

}
