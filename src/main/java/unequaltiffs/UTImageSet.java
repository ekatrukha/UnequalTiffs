package unequaltiffs;

import java.awt.Color;
import java.awt.image.IndexColorModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.measure.Calibration;
import ij.process.LUT;
import io.scif.config.SCIFIOConfig;
import io.scif.config.SCIFIOConfig.ImgMode;
import io.scif.img.ImgOpener;
import io.scif.img.SCIFIOImgPlus;
import net.imglib2.Cursor;
import net.imglib2.img.Img;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

public class UTImageSet < T extends RealType< T > & NativeType< T > >
{
	public int nChannels;
	public int nSlices;
	public int nTimePoints;
	public boolean bMultiCh = false;
	public ArrayList<long []> im_dims;
	public ArrayList<Img<T>> imgs_in;
	public String [] sFileNamesShort;
	public Calibration cal;
	public int [] ipDim;
	public int nDimN;
	
	long[][] singleBox = null;
	public boolean bInit = false;
	
	/** colors for each channel **/
	public Color [] colorsCh;
	
	/** intensity range for channels **/
	public double [][] channelRanges;	
	
	ArrayList<SCIFIOImgPlus< T >> filesOpened = null;
	
	/** function reads all tiffs from the sPath and ensures they have the same
	 * dimensions and number of channels.
	 * If succeeded, fills all member variables of this class **/
	@SuppressWarnings("unchecked")
	boolean initializeAndCheckConsistensy(String sPath, String sFileExtension)
	{
		// getting list of files
		List<String> filenames = null;
		if (sPath != null)
		{
			
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
				IJ.log("Cannot find any "+sFileExtension+" files in provided folder, aborting.");
				return false;
			}
			else
			{
				IJ.log("Found " +Integer.toString(filenames.size())+" "+sFileExtension+" files.");
			}
		}
		else
			return false;
		
		fillFilenamesArray(filenames, sFileExtension);
		IJ.log("Analyzing dimensions:");
		
		ImagePlus ipFirst = IJ.openImage(filenames.get(0));
		cal = ipFirst.getCalibration();
		ipDim = ipFirst.getDimensions();
		String sDims = "XY";
		nChannels = ipFirst.getNChannels();
		nSlices = ipFirst.getNSlices();
		nTimePoints = ipFirst.getNFrames();
		getChannelsColors(ipFirst);
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

		
		IJ.showStatus("Loading files....");
		
		IJ.showProgress(0,filenames.size());
		ImgOpener imgOpener = new ImgOpener();
		SCIFIOConfig config = new SCIFIOConfig();
		config.imgOpenerSetImgModes( ImgMode.CELL );
		//Img< ? > firstImg =  imgOpener.openImgs( filenames.get(0), config ).get( 0 );
		
		filesOpened = new ArrayList<SCIFIOImgPlus< T >>();
		filesOpened.add((SCIFIOImgPlus<T>) imgOpener.openImgs( filenames.get(0), config ).get( 0 ));
		//SCIFIOImgPlus< ? > firstImg =  imgOpener.openImgs( filenames.get(0), config ).get( 0 );
		
		Cursor<T> cursorTest = filesOpened.get(0).getImg().cursor();
		cursorTest.fwd();
		@SuppressWarnings("rawtypes")
		Class TypeIni = cursorTest.get().getClass();
		nDimN = filesOpened.get(0).getImg().numDimensions();
		im_dims = new ArrayList<long[]>();
		//SCIFIOImgPlus< ? > imageCell;
		imgs_in = new ArrayList<Img<T>>();
		imgs_in.add((Img<T>) filesOpened.get(0).getImg());
		im_dims.add(filesOpened.get(0).getImg().dimensionsAsLongArray());
		for(int i=1;i<filenames.size();i++)
		{
			IJ.showProgress(i,filenames.size());
			filesOpened.add((SCIFIOImgPlus<T>) imgOpener.openImgs( filenames.get(i), config ).get( 0 ));
			//imageCell = imgOpener.openImgs( filenames.get(i), config ).get( 0 );
			//check the bit depth
			cursorTest = filesOpened.get(i).getImg().cursor();
			cursorTest.fwd();
			if(!TypeIni.isInstance(cursorTest.get()))
			{
				IJ.log("ERROR! File "+filenames.get(i)+"\nhas different bit depth, aborting.");
				return false;
			}
			//simple check of dimension number
			if(filesOpened.get(i).getImg().numDimensions()!=nDimN)
			{
				IJ.log("ERROR! File "+filenames.get(i)+"\nhas different dimensions, aborting.");				
				return false;
			}
			//IJ.log(Integer.toString(imageCell.numDimensions()));
			im_dims.add(filesOpened.get(i).getImg().dimensionsAsLongArray());
			if(bMultiCh)
			{
				if(im_dims.get(i)[2]!=nChannels)
				{
					IJ.log("ERROR! File "+filenames.get(i)+"\nhas different number of channels, aborting.");				
					return false ;
				}
			}
			imgs_in.add((Img<T>) filesOpened.get(i).getImg());

		}
		IJ.showStatus("Loading files....done.");
		
		IJ.showProgress(2,2);
		bInit = true;
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
	
	public void fillFilenamesArray(List<String> fullPath, String sFileExtension)
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
	
	public long [][] getSingleBox()
	{
		if(!bInit)
			return null;
		if(singleBox == null)
		{
			singleBox = new long[2][nDimN];
			for(int i = 0; i<imgs_in.size();i++)
			{
					for(int d=0;d<nDimN;d++)
					{
						if(im_dims.get(i)[d]>singleBox[1][d])
						{
							singleBox[1][d] =  im_dims.get(i)[d];
						}
					}
			}			
		}
		return singleBox;
	}
	
	/** creates and fills array colorsCh with channel colors,
	 * taken from Christian Tischer reply in this thread
	 * https://forum.image.sc/t/composite-image-channel-color/45196/3 **/
	public void getChannelsColors(ImagePlus imp)
	{
		colorsCh = new Color[imp.getNChannels()];
		channelRanges = new double [2][imp.getNChannels()];
	      for ( int c = 0; c < imp.getNChannels(); ++c )
	        {
	            if ( imp instanceof CompositeImage )
	            {
	                CompositeImage compositeImage = ( CompositeImage ) imp;
					LUT channelLut = compositeImage.getChannelLut( c + 1 );
					int mode = compositeImage.getMode();
					if ( channelLut == null || mode == CompositeImage.GRAYSCALE )
					{
						colorsCh[c] = Color.WHITE;
					}
					else
					{
						IndexColorModel cm = channelLut.getColorModel();
						if ( cm == null )
						{
							colorsCh[c] = Color.WHITE;
						}
						else
						{
							int i = cm.getMapSize() - 1;
							colorsCh[c] = new Color(cm.getRed( i ) ,cm.getGreen( i ) ,cm.getBlue( i ) );

						}

					}

					compositeImage.setC( c + 1 );
					channelRanges[0][c]=(int)imp.getDisplayRangeMin();
					channelRanges[1][c]=(int)imp.getDisplayRangeMax();
	            }
	            else
	            {
	            	colorsCh[c] = Color.WHITE;
	            	channelRanges[0][c]=(int)imp.getDisplayRangeMin();
	            	channelRanges[1][c]=(int)imp.getDisplayRangeMax();
	            }
	        }
	}
}
