package unequaltiffs;

import java.util.ArrayList;

import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.cache.img.CellLoader;
import net.imglib2.cache.img.ReadOnlyCachedCellImgFactory;
import net.imglib2.cache.img.ReadOnlyCachedCellImgOptions;
import net.imglib2.cache.img.SingleCellArrayImg;
import net.imglib2.cache.img.optional.CacheOptions.CacheType;
import net.imglib2.img.Img;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class UTConcatenate < T extends RealType< T > & NativeType< T > >{
	
	UTImageSet<T> imageSet;
	
	final ArrayList<IntervalView<T>> intervals;
	final long [] singleBoxDims;
	final int nImgN;


	
	public UTConcatenate(final UTImageSet<T> imageSet_)
	{
		imageSet = imageSet_;
		singleBoxDims = imageSet.getSingleBoxDims();
		intervals = new ArrayList<IntervalView<T>>();
		nImgN = imageSet.im_dims.size();
	}
	public Img< T > concatenate(final int nAlignConc, final String sConcDimension)
	{
		
		long [][] singleBoxInterval = new long [2][singleBoxDims.length];
		for(int d =0;d<singleBoxDims.length;d++)
		{
			singleBoxInterval[1][d] = singleBoxDims[d]-1;
		}
		
		//align images
		if(nAlignConc == UnequalTiffs.ALIGN_ZERO)
		{
			for(int i = 0; i<imageSet.im_dims.size();i++)
			{
				intervals.add(Views.interval(Views.extendZero(imageSet.imgs_in.get(i)),new FinalInterval(singleBoxInterval[0],singleBoxInterval[1])));
			}
		}
		else
		{
			long [] nShifts = new long[imageSet.nDimN];
			for(int i = 0; i<imageSet.im_dims.size();i++)
			{
				for(int d=0;d<imageSet.nDimN;d++)
				{
					nShifts[d] = (int) Math.floor(0.5*(singleBoxDims[d]-imageSet.im_dims.get(i)[d]));
				}
				
				intervals.add(Views.interval(
											Views.translate(
													Views.extendZero(imageSet.imgs_in.get(i))
											,nShifts),
							new FinalInterval(singleBoxInterval[0],singleBoxInterval[1])));
			}
		}
		
		
		final long[] dimensions = new long[imageSet.nDimN+1];
		for(int d=0;d<imageSet.nDimN;d++)
		{
			dimensions[d] = singleBoxDims[d];
		}
		dimensions[imageSet.nDimN] = nImgN;
		
		final int[] cellDimensions = new int[] { (int)singleBoxDims[0], (int)singleBoxDims[1], 1 };
		final CellLoader< T > loader = new CellLoader< T >()
		{
			@Override
			public void load( final SingleCellArrayImg< T, ? > cell ) throws Exception
			{		
				final int ind = (int) cell.min(imageSet.nDimN);
				IntervalView<T> slice = intervals.get(ind);
				IntervalView<T> sliceCell = Views.hyperSlice(cell, imageSet.nDimN,ind);
				for(int d = imageSet.nDimN-1;d>=2;d--)
				{
					slice = Views.hyperSlice(slice, d, (int)cell.min(d));
					sliceCell = Views.hyperSlice(sliceCell, d, (int)cell.min(d));
				}
				LoopBuilder.setImages(slice, sliceCell).forEachPixel((s, t) -> t.set(s));

			}
		
		};

		Cursor<T> cursorTest = imageSet.imgs_in.get(0).cursor();
		cursorTest.fwd();
		return new ReadOnlyCachedCellImgFactory().create(
				dimensions,
				cursorTest.get(),
				loader,
				ReadOnlyCachedCellImgOptions.options().cellDimensions( cellDimensions ).cacheType(CacheType.BOUNDED).maxCacheSize(3) );
	}
}
