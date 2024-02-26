package unequaltiff;

import java.util.ArrayList;

import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.img.CellLoader;
import net.imglib2.cache.img.ReadOnlyCachedCellImgFactory;
import net.imglib2.cache.img.ReadOnlyCachedCellImgOptions;
import net.imglib2.cache.img.SingleCellArrayImg;
import net.imglib2.cache.img.optional.CacheOptions.CacheType;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class UTMontage< T extends RealType< T > & NativeType< T > > {

	final ArrayList<long []> im_dims;
	final ArrayList<Img<T>> imgs_in;	
	final ArrayList<IntervalView<T>> intervals;
	int nCols;
	int nRows;
	final boolean bMultiCh;
	int nDimN;
	int [][] indexes; 
	final long[][] singleBox;
	public UTMontage(final ArrayList<Img<T>> imgs_in_, final ArrayList<long []> im_dims_, final boolean bMultiCh_)
	{
		imgs_in = imgs_in_;
		im_dims = im_dims_;
		bMultiCh = bMultiCh_;
		nDimN = im_dims.get(0).length;
		singleBox = new long[2][nDimN];
		intervals = new ArrayList<IntervalView<T>>();
	}
	
	public void makeMontage(final int nRows_, final int nCols_)
	{
		nCols = nCols_;
		nRows = nRows_;
		
		//calculate the range of boxes size
		//nDimN = im_dims.get(0).length;
		//int[] singleBox = new int[nDimN];
		indexes = new int[nRows][nCols];
		int nR=0;
		int nC=0;
		for(int i = 0; i<im_dims.size();i++)
		{
			for(int d=0;d<nDimN;d++)
			{
				if(im_dims.get(i)[d]>singleBox[1][d])
				{
					singleBox[1][d] =  im_dims.get(i)[d];
				}
			}
			indexes[nR][nC]=i;
			nR++;
			if(nR >= nRows)
			{
				nR = 0;
				nC++;
			}
		}
		
		for(int i = 0; i<im_dims.size();i++)
		{
			intervals.add(Views.interval(Views.extendZero(imgs_in.get(i)),new FinalInterval(singleBox[0],singleBox[1])));
		}
		
		final long[] dimensions = new long[nDimN];
		dimensions[0] = nRows*singleBox[1][0];
		dimensions[1] = nCols*singleBox[1][1];
		if(nDimN>2)
		{
			for(int d=2;d<nDimN;d++)
			{
				dimensions[d]=singleBox[1][d];
			}
		}
		final int[] cellDimensions;
		if(bMultiCh)
		{
			cellDimensions = new int[] { (int)singleBox[1][0], (int)singleBox[1][1], (int)singleBox[1][2] };
		}
		else
		{			
			cellDimensions = new int[] { (int)singleBox[1][0], (int)singleBox[1][1], 1 };
		}
		final CellLoader< T > loader = new CellLoader< T >()
		{
			@Override
			public void load( final SingleCellArrayImg< T, ? > cell ) throws Exception
			{
				final int x = ( int ) cell.min( 0 );
				final int y = ( int ) cell.min( 1 );
				final int nRow = Math.round(x/singleBox[1][0]);
				final int nCol = Math.round(y/singleBox[1][1]);				
				final int imInd = indexes[nRow][nCol];
				
				final Cursor<T> curCell = cell.localizingCursor();
				final RandomAccess<T> ra = intervals.get(imInd).randomAccess();

				long [] pos = new long [nDimN];
				while(curCell.hasNext())
				{
					curCell.fwd();
					curCell.localize(pos);
					pos[0]-=x;
					pos[1]-=y;
					ra.setPosition(pos);
					curCell.get().set(ra.get());
				}
			}
		
		};
		
		Cursor<T> cursorTest = imgs_in.get(0).cursor();
		cursorTest.fwd();
		final Img< T > img = new ReadOnlyCachedCellImgFactory().create(
				dimensions,
				cursorTest.get(),
				loader,
				ReadOnlyCachedCellImgOptions.options().cellDimensions( cellDimensions ).cacheType(CacheType.BOUNDED) );
		ImageJFunctions.show( (RandomAccessibleInterval<T>) img );
	}
}
