package unequaltiffs;

import java.util.ArrayList;

import org.scijava.ui.behaviour.DragBehaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;

import bdv.tools.transformation.TransformedSource;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.SourceGroup;
import btbvv.core.VolumeViewerPanel;
import btbvv.vistools.Bvv;
import btbvv.vistools.BvvFunctions;
import btbvv.vistools.BvvStackSource;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class UTExploreBVV < T extends RealType< T > & NativeType< T > >
{
	
	UTImageSet<T> imageSet;
	final ArrayList<IntervalView<T>> intervals;
	final int nImgN;
	
	int nCols;
	int nRows;
	int [][] indexes; 
	int [][] indexes_inv; 
	final long[][] singleBox;
	long [][] nFinalBox;
	public double [] globCal;
	ArrayList<BvvStackSource< ? >> bvv_sources = new ArrayList<BvvStackSource< ? >>();
	ArrayList<AffineTransform3D> arrTr = new ArrayList<AffineTransform3D>();
	public VolumeViewerPanel viewer;
	/** main instance of BVV **/
	public  BvvStackSource< ? > bvv_main = null;
	
	private final AffineTransform3D affineDragStart = new AffineTransform3D();

	/**
	 * Current transform during mouse dragging.
	 */
	private final AffineTransform3D affineDragCurrent = new AffineTransform3D();
	
	/**
	 * Coordinates where mouse dragging started.
	 */
	private double oX, oY;
	
	/**
	 * One step of rotation (radian).
	 */
	final private static double step = Math.PI / 180;
	Rotate dragRotate; 
	
	public UTExploreBVV(final UTImageSet<T> imageSet_)
	{
		imageSet = imageSet_;
		singleBox = imageSet.getSingleBox();
		intervals = new ArrayList<IntervalView<T>>();
		nImgN = imageSet.im_dims.size();
		dragRotate = new Rotate( 1.0 );
	}
	
	public void browseBVV()
	{
		globCal = new double[3];
		globCal[0]=imageSet.cal.pixelWidth;
		globCal[1]=imageSet.cal.pixelHeight;
		globCal[2]=imageSet.cal.pixelDepth;
		makeIntervals();
	
		Bvv Tempbvv = BvvFunctions.show( Bvv.options().
				dCam(2000.0).
				dClipNear(1000).
				dClipFar(1000)
				);
		double[] currShift;
		for(int i=0;i<nImgN;i++)
		{
	
			arrTr.add(new AffineTransform3D() );
			arrTr.get(i).identity();
			currShift = new double[5];
			for(int j=0;j<2;j++)
			{
				currShift[j]+=indexes_inv[i][j]*singleBox[1][j];
			}
			arrTr.get(i).translate(currShift);
			bvv_sources.add(BvvFunctions.show( intervals.get(i), imageSet.sFileNamesShort[i], Bvv.options().addTo(Tempbvv).sourceTransform(arrTr.get(i))));
		}
		bvv_main = bvv_sources.get(0);

		viewer = bvv_main.getBvvHandle().getViewerPanel();
		setInitialTransform();
		resetViewXY();
		final SourceGroup g = new SourceGroup();
		viewer.state().addGroup( g );
		final String a_group_name = "all";
		viewer.state().setGroupName( g, a_group_name );
		for(int i=0;i<nImgN;i++)
		{
			viewer.state().addSourcesToGroup( bvv_sources.get(i).getSources(), g );
		}
		viewer.state().setCurrentGroup(g);
		viewer.state().setGroupActive(g, true);
		
		Behaviours behaviours = new Behaviours( new InputTriggerConfig() );
		behaviours.install( bvv_main.getBvvHandle().getTriggerbindings(), "my-new-behaviours" );
		behaviours.behaviour( dragRotate, "print global pos", "D" );
		
		
		//test transform
		AffineTransform3D t = new AffineTransform3D();
		t.identity();
		t.rotate(2, Math.PI/4);

		double [] tr = new double [3];
		tr[2]=0.5*singleBox[1][2];
		for ( SourceAndConverter< ? > source : viewer.state().getSources() )
		{
			
			AffineTransform3D curr = new AffineTransform3D();
			
			
			(( TransformedSource< ? > ) source.getSpimSource() ).getSourceTransform(0, 0, curr);
			//curr.concatenate(viewer.state().getViewerTransform());
			//curr.preConcatenate(viewer.state().getViewerTransform());
			//curr.apply(centerC, centerC);
			
			//get center of the cube,
			//transform it using current transform
			//move there
			//rotate
			//move back
			
			
			tr[0]=curr.get( 0, 3 )+0.5*singleBox[1][0];
			tr[1]=curr.get( 1, 3 )+0.5*singleBox[1][1];
			
			AffineTransform3D tFinal = new AffineTransform3D();
			tFinal.identity();
			
			tFinal.set( (-1.0)*(tr[0]), 0, 3 );
			tFinal.set( (-1.0)*(tr[1]), 1, 3 );
			tFinal.set( (-1.0)*(tr[2]), 2, 3 );
			tFinal.preConcatenate(t);
			tFinal.translate(tr);
			
			//tFinal.concatenate(t);
		//	tFinal.set( (curr.get( 0, 3 )+0.5*singleBox[1][0]), 0, 3 );
			//tFinal.set( (curr.get( 1, 3 )+0.5*singleBox[1][1]), 1, 3 );
			//tFinal.set( (0.5*singleBox[1][2]), 2, 3 );

			//affineDragCurrent.set( affineDragCurrent.get( 0, 3 ) - oX, 0, 3 );
			//affineDragCurrent.set( affineDragCurrent.get( 1, 3 ) - oY, 1, 3 );
			//(( TransformedSource< ? > ) source.getSpimSource() ).setIncrementalTransform(tFinal);
			(( TransformedSource< ? > ) source.getSpimSource() ).setFixedTransform(tFinal);
			
		}
		viewer.requestRepaint();
	
	}
	
	void setTransform(AffineTransform3D t)
	{
		double [] tr = new double [3];
		tr[2]=0.5*singleBox[1][2];
		for ( SourceAndConverter< ? > source : viewer.state().getSources() )
		{
			
			AffineTransform3D curr = new AffineTransform3D();
			
			
			(( TransformedSource< ? > ) source.getSpimSource() ).getSourceTransform(0, 0, curr);
			//curr.concatenate(viewer.state().getViewerTransform());
			//curr.preConcatenate(viewer.state().getViewerTransform());
			//curr.apply(centerC, centerC);
			
			tr[0]=curr.get( 0, 3 )+0.5*singleBox[1][0];
			tr[1]=curr.get( 1, 3 )+0.5*singleBox[1][1];
			
			AffineTransform3D tFinal = new AffineTransform3D();
			tFinal.identity();
			tFinal.set( (-1.0)*(tr[0]), 0, 3 );
			tFinal.set( (-1.0)*(tr[1]), 1, 3 );
			tFinal.set( (-1.0)*(tr[2]), 2, 3 );
			tFinal.preConcatenate(t);
			tFinal.translate(tr);
			
			//tFinal.concatenate(t);
		//	tFinal.set( (curr.get( 0, 3 )+0.5*singleBox[1][0]), 0, 3 );
			//tFinal.set( (curr.get( 1, 3 )+0.5*singleBox[1][1]), 1, 3 );
			//tFinal.set( (0.5*singleBox[1][2]), 2, 3 );

			//affineDragCurrent.set( affineDragCurrent.get( 0, 3 ) - oX, 0, 3 );
			//affineDragCurrent.set( affineDragCurrent.get( 1, 3 ) - oY, 1, 3 );
			//(( TransformedSource< ? > ) source.getSpimSource() ).setIncrementalTransform(tFinal);
			(( TransformedSource< ? > ) source.getSpimSource() ).setFixedTransform(tFinal);
			
		}
		viewer.requestRepaint();
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
			//intervals.add(Views.translate(getBDVIntervalView(i), currShift) );
			intervals.add(getBDVIntervalView(i));
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
			out = Views.permute(out, 4,3);
		}
		return out;
		
	}
	public void setInitialTransform()
	{
		AffineTransform3D t = new AffineTransform3D();
		t.identity();
		t.scale(globCal[0],globCal[1],globCal[2]);
		viewer.state().setViewerTransform(t);
	}
	public void resetViewXY()
	{
		
		double scale;
		long [][] nBox;

		nBox = new long [2][3];
		nBox[1][0] = singleBox[1][0]*nCols;
		nBox[1][1] = singleBox[1][0]*nRows;
		nBox[1][2] = singleBox[1][2];
		
		double nW = (double)(nBox[1][0]-nBox[0][0])*globCal[0];
		double nH = (double)(nBox[1][1]-nBox[0][1])*globCal[1];
		double nWoff = (double)(2.0*nBox[0][0])*globCal[0];
		double nHoff = (double)(2.0*nBox[0][1])*globCal[1];
		double nDoff = (double)(2.0*nBox[0][2])*globCal[2];
		
		double sW = viewer.getWidth();
		double sH = viewer.getHeight();
		
		if(sW/nW<sH/nH)
		{
			scale = sW/nW;
		}
		else
		{
			scale = sH/nH;
		}
		scale = 0.9*scale;
		AffineTransform3D t = new AffineTransform3D();
		t.identity();

		t.scale(globCal[0]*scale, globCal[1]*scale, globCal[2]*scale);
		t.translate(0.5*(sW-scale*(nW+nWoff)),0.5*(sH-scale*(nH+nHoff)),(-0.5)*scale*(nDoff));
		
		//AnisotropicTransformAnimator3D anim = new AnisotropicTransformAnimator3D(viewer.state().getViewerTransform(),t,0,0,(long)(btdata.nAnimationDuration*0.5));
		//viewer.setTransformAnimator(anim);
		viewer.state().setViewerTransform(t);
			
	}
	private class Rotate implements DragBehaviour
	{
		private final double speed;

		public Rotate( final double speed )
		{
			this.speed = speed;
		}

		@Override
		public void init( final int x, final int y )
		{
			oX = x;
			oY = y;
			//transform.get( affineDragStart );
		}

		@Override
		public void drag( final int x, final int y )
		{
			final double dX = oX - x;
			final double dY = oY - y;

			affineDragCurrent.set( affineDragStart );

			// center shift
			//affineDragCurrent.set( affineDragCurrent.get( 0, 3 ) - oX, 0, 3 );
			//affineDragCurrent.set( affineDragCurrent.get( 1, 3 ) - oY, 1, 3 );
			final double v = step * speed;
			//affineDragCurrent.rotate( 0, -dY * v );
			//affineDragCurrent.rotate( 1, dX * v );
			affineDragCurrent.rotate( 2, dX * v );

			// center un-shift
			//affineDragCurrent.set( affineDragCurrent.get( 0, 3 ) + oX, 0, 3 );
			//affineDragCurrent.set( affineDragCurrent.get( 1, 3 ) + oY, 1, 3 );
			//System.out.println(Double.toString(oX)+Double.toString(oY));
			//transform.set( affineDragCurrent );
			setTransform(affineDragCurrent);
		}

		@Override
		public void end( final int x, final int y )
		{}
	}
}
