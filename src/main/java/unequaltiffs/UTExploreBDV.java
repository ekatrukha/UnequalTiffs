package unequaltiffs;

import java.util.ArrayList;

import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.IntervalView;

public class UTExploreBDV < T extends RealType< T > & NativeType< T > >
{
	
	UTImageSet<T> imageSet;
	final ArrayList<IntervalView<T>> intervals;
	final int nImgN;
	public UTExploreBDV(final UTImageSet<T> imageSet_)
	{
		imageSet = imageSet_;

		intervals = new ArrayList<IntervalView<T>>();
		nImgN = imageSet.im_dims.size();
	}
}
