package unequaltiffs;

import java.util.ArrayList;

import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.volatiles.VolatileViews;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class UTExploreBDV < T extends RealType< T > & NativeType< T > >
{
	
	UTImageSet<T> imageSet;
	final ArrayList<IntervalView<T>> intervals;
	final int nImgN;
	
	int nCols;
	int nRows;
	int [][] indexes; 
	int [][] indexes_inv; 
	final long[][] singleBox;
	
	public UTExploreBDV(final UTImageSet<T> imageSet_)
	{
		imageSet = imageSet_;
		singleBox = imageSet.getSingleBox();
		intervals = new ArrayList<IntervalView<T>>();
		nImgN = imageSet.im_dims.size();
	}
	
	public void browseBDV()
	{
		makeIntervals();
	
		
		final Bdv bdv = BdvFunctions.show( intervals.get(0), imageSet.sFileNamesShort[0], 
				Bdv.options().sourceTransform(
						imageSet.cal.pixelWidth,
						imageSet.cal.pixelHeight,
						imageSet.cal.pixelDepth	));
		for(int i=1;i<nImgN;i++)
		{
			BdvFunctions.show( intervals.get(i), imageSet.sFileNamesShort[i], 
					BdvOptions.options()
					.addTo( bdv )
					.sourceTransform(
					imageSet.cal.pixelWidth,
					imageSet.cal.pixelHeight,
					imageSet.cal.pixelDepth	) );
		}
	}
	
	void makeIntervals()
	{
		nCols = (int)Math.sqrt(nImgN);
		nRows = nCols;
		int n = nImgN - nCols*nRows;
		
		if (n>0) nCols += (int)Math.ceil((double)n/nRows);
		indexes = new int[nCols][nRows];
		indexes_inv = new int[nImgN][2]; 
		int nR = 0;
		int nC = 0;
		for(int i = 0; i<nCols*nRows;i++)
		{
			if(i<nImgN)
			{
				indexes[nC][nR] = i;
				indexes_inv[i][0] = nC;
				indexes_inv[i][1] = nR;
			}
			else
			{
				//empty cell
				indexes[nC][nR] = -1;
			}
			nC++;
			if(nC >= nCols)
			{
				nC = 0;
				nR++;
			}
		}
		long[] currShift;
		for(int i =0; i<nImgN;i++)
		{		
			currShift = new long[5];
			for(int j=0;j<2;j++)
			{
				currShift[j]+=indexes_inv[i][j]*singleBox[1][j];
			}
			intervals.add(Views.translate(getBDVIntervalView(i), currShift) );
			//intervals.add(getBDVIntervalView(i));
		}
	}
	
	IntervalView<T> getBDVIntervalView(final int i)
	{
		IntervalView<T> out;
		
		if(!imageSet.bMultiCh)
		{
			//add dimension for the channels
			out = Views.addDimension(imageSet.imgs_in.get(i), 0, 0);
		}
		else
		{
			//change the order of C and Z
			out =  Views.permute(imageSet.imgs_in.get(i), 2,3);
		}	
		
		if(imageSet.nTimePoints ==1)
		{
			out = Views.addDimension(out, 0, 0);
			//test = all_ch_RAI.dimensionsAsLongArray();
			if(imageSet.nChannels==1)
			{
				out =Views.permute(out, 3,4);
			}
			//test = all_ch_RAI.dimensionsAsLongArray();
		}
		//finally change C and T (or it can be already fine, if we added C dimension)
		if(imageSet.nChannels>1)
		{
			out =Views.permute(out, 4,3);
		}
		return out;
		
	}
}
