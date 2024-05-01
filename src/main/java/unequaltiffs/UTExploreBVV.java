package unequaltiffs;

import java.util.ArrayList;

import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.LinAlgHelpers;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

import org.scijava.ui.behaviour.DragBehaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Actions;
import org.scijava.ui.behaviour.util.Behaviours;

import bdv.tools.transformation.TransformedSource;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.SourceGroup;
import btbvv.core.VolumeViewerPanel;
import btbvv.vistools.Bvv;
import btbvv.vistools.BvvFunctions;
import btbvv.vistools.BvvStackSource;

public class UTExploreBVV< T extends RealType< T > & NativeType< T > >
{

	UTImageSet< T > imageSet;

	final ArrayList< IntervalView< T > > intervals;

	final int nImgN;

	int nCols;

	int nRows;

	int[][] indexes;

	int[][] indexes_inv;

	final long[] singleBoxDims;

	long[][] nFinalBox;

	public double[] globCal;

	ArrayList< BvvStackSource< ? > > bvv_sources = new ArrayList<>();

	public VolumeViewerPanel viewer;

	/** main instance of BVV **/
	public BvvStackSource< ? > bvv_main = null;

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

	double[] centBox = new double[ 3 ];

	Rotate dragRotate;

	public UTExploreBVV( final UTImageSet< T > imageSet_ )
	{
		imageSet = imageSet_;
		singleBoxDims = imageSet.getSingleBoxDims();
		intervals = new ArrayList<>();
		nImgN = imageSet.im_dims.size();
		dragRotate = new Rotate( 0.3 );
	}

	public void browseBVV()
	{
		globCal = new double[ 3 ];
		globCal[ 0 ] = imageSet.cal.pixelWidth;
		globCal[ 1 ] = imageSet.cal.pixelHeight;
		globCal[ 2 ] = imageSet.cal.pixelDepth;

		makeIntervals();

		Bvv Tempbvv = BvvFunctions.show( Bvv.options().dCam( 2000.0 ).dClipNear( 1000 ).dClipFar( 1000 )//.
		//cacheBlockSize( 32 ).
		//maxCacheSizeInMB( 4000)
		);
		double[] currShift;
		AffineTransform3D arrTr = new AffineTransform3D();
		for ( int i = 0; i < nImgN; i++ )
		{

			arrTr.identity();
			currShift = new double[ 5 ];
			for ( int j = 0; j < 2; j++ )
			{
				currShift[ j ] += indexes_inv[ i ][ j ] * singleBoxDims[ j ];
			}
			arrTr.translate( currShift );
			if ( !imageSet.bMultiCh )
			{
				bvv_sources.add( BvvFunctions.show( intervals.get( i ), imageSet.sFileNamesShort[ i ],
						Bvv.options().addTo( Tempbvv ).sourceTransform( arrTr ) ) );
				bvv_sources.get( i ).setColor( new ARGBType( imageSet.colorsCh[ 0 ].getRGB() ) );
			}
			else
			{
				for ( int nCh = 0; nCh < imageSet.nChannels; nCh++ )
				{
					bvv_sources.add( BvvFunctions.show( Views.hyperSlice( intervals.get( i ), 4, nCh ),
							imageSet.sFileNamesShort[ i ] + "_ch" + Integer.toString( nCh + 1 ),
							Bvv.options().addTo( Tempbvv ).sourceTransform( arrTr ) ) );
					bvv_sources.get( bvv_sources.size() - 1 ).setColor( new ARGBType( imageSet.colorsCh[ nCh ].getRGB() ) );
				}
			}

		}
		bvv_main = bvv_sources.get( 0 );

		viewer = bvv_main.getBvvHandle().getViewerPanel();
		setInitialTransform();
		resetViewXY();
		//final SourceGroup g = new SourceGroup();
		final SourceGroup g = viewer.state().getGroups().get( 0 );
		viewer.state().addGroup( g );
		String a_group_name = "all";
		viewer.state().setGroupName( g, a_group_name );
		for ( int i = 0; i < bvv_sources.size(); i++ )
		{
			viewer.state().addSourcesToGroup( bvv_sources.get( i ).getSources(), g );
		}
		viewer.state().setCurrentGroup( g );
		viewer.state().setGroupActive( g, true );
		if ( imageSet.bMultiCh )
		{
			for ( int nCh = 0; nCh < imageSet.nChannels; nCh++ )
			{
				SourceGroup gCh;
				if ( viewer.state().getGroups().size() < ( nCh + 1 ) )
				{
					gCh = new SourceGroup();
					viewer.state().addGroup( gCh );
				}
				else
				{
					gCh = viewer.state().getGroups().get( nCh + 1 );
				}
				a_group_name = "ch_" + Integer.toString( nCh + 1 ) + "_all";
				viewer.state().setGroupName( gCh, a_group_name );
				for ( int i = 0; i < nImgN; i++ )
				{
					viewer.state().addSourcesToGroup( bvv_sources.get( i * imageSet.nChannels + nCh ).getSources(), gCh );
				}

			}
		}

		Behaviours behaviours = new Behaviours( new InputTriggerConfig() );
		behaviours.install( bvv_main.getBvvHandle().getTriggerbindings(), "my-new-behaviours" );
		behaviours.behaviour( dragRotate, "rotate each volume", "D" );
		// add some actions
		Actions actions = new Actions( new InputTriggerConfig() );
		actions.install( bvv_main.getBvvHandle().getKeybindings(), "my-new-actions" );

		actions.runnableAction( () -> {
			resetViewXY();
		}, "reset whole transform", "1" );
		actions.runnableAction( () -> {
			resetViewVolumesXY();
		}, "align volumes XY", "2" );
		actions.runnableAction( () -> {
			resetViewVolumesXZ();
		}, "align volumes XZ", "3" );
		actions.runnableAction( () -> {
			resetViewVolumesYZ();
		}, "align volumes YZ", "4" );

	}

	void updateTransform( AffineTransform3D t )
	{
		AffineTransform3D curr = new AffineTransform3D();
		AffineTransform3D currFixed = new AffineTransform3D();
		AffineTransform3D tFinal = new AffineTransform3D();
		double[] currShift = new double[ 3 ];

		//bvv_sources.get(0).g
		for ( int i = 0; i < bvv_sources.size(); i++ )
		{
			for ( SourceAndConverter< ? > source : bvv_sources.get( i ).getSources() )
			{

				TransformedSource< ? > tS = ( ( TransformedSource< ? > ) source.getSpimSource() );

				tS.getSourceTransform( 0, 0, curr );
				curr.apply( centBox, currShift );
				tS.getFixedTransform( currFixed );
				tFinal.set( currFixed );
				LinAlgHelpers.scale( currShift, -1.0, currShift );
				tFinal.translate( currShift );
				tFinal.preConcatenate( t );
				LinAlgHelpers.scale( currShift, -1.0, currShift );
				tFinal.translate( currShift );
				tS.setFixedTransform( tFinal );
			}
		}
		viewer.requestRepaint();
	}

	void setTransform( AffineTransform3D t )
	{
		AffineTransform3D curr = new AffineTransform3D();
		//AffineTransform3D currFixed = new AffineTransform3D();
		AffineTransform3D tFinal = new AffineTransform3D();
		double[] currShift = new double[ 3 ];

		//bvv_sources.get(0).g
		for ( int i = 0; i < bvv_sources.size(); i++ )
		{
			for ( SourceAndConverter< ? > source : bvv_sources.get( i ).getSources() )
			{

				TransformedSource< ? > tS = ( ( TransformedSource< ? > ) source.getSpimSource() );

				tS.getSourceTransform( 0, 0, curr );
				curr.apply( centBox, currShift );
				//tS.getFixedTransform(currFixed);
				//tFinal.set(currFixed);
				tFinal.identity();
				LinAlgHelpers.scale( currShift, -1.0, currShift );
				tFinal.translate( currShift );
				tFinal.preConcatenate( t );
				LinAlgHelpers.scale( currShift, -1.0, currShift );
				tFinal.translate( currShift );
				tS.setFixedTransform( tFinal );
			}
		}
		viewer.requestRepaint();
	}

	void makeIntervals()
	{
		nCols = ( int ) Math.sqrt( nImgN );
		nRows = nCols;
		int n = nImgN - nCols * nRows;

		if ( n > 0 )
			nCols += ( int ) Math.ceil( ( double ) n / nRows );
		indexes = new int[ nCols ][ nRows ];
		indexes_inv = new int[ nImgN ][ 2 ];
		int nR = 0;
		int nC = 0;
		for ( int i = 0; i < nCols * nRows; i++ )
		{
			if ( i < nImgN )
			{
				indexes[ nC ][ nR ] = i;
				indexes_inv[ i ][ 0 ] = nC;
				indexes_inv[ i ][ 1 ] = nR;
			}
			else
			{
				//empty cell
				indexes[ nC ][ nR ] = -1;
			}
			nC++;
			if ( nC >= nCols )
			{
				nC = 0;
				nR++;
			}
		}

		//adjust box dimensions to allow rotation

		long maxL = Math.max( singleBoxDims[ 0 ], singleBoxDims[ 1 ] );
		if ( imageSet.bMultiCh )
		{
			maxL = Math.max( maxL, singleBoxDims[ 3 ] );
		}
		else
		{
			maxL = Math.max( maxL, singleBoxDims[ 2 ] );
		}
		for ( int d = 0; d < 2; d++ )
		{
			singleBoxDims[ d ] = maxL;
		}
		if ( imageSet.bMultiCh )
		{
			singleBoxDims[ 3 ] = maxL;
		}
		else
		{
			singleBoxDims[ 2 ] = maxL;
		}

		for ( int j = 0; j < nImgN; j++ )
		{
			long[] nShifts = new long[ imageSet.nDimN ];
			for ( int i = 0; i < imageSet.im_dims.size(); i++ )
			{
				for ( int d = 0; d < imageSet.nDimN; d++ )
				{
					if ( !( imageSet.bMultiCh && d == 2 ) )
					{
						nShifts[ d ] = ( int ) Math.floor( 0.5 * ( singleBoxDims[ d ] - imageSet.im_dims.get( j )[ d ] ) );
					}
				}
			}
			intervals.add( getBDVIntervalView(
					Views.translate( imageSet.imgs_in.get( j ), nShifts ) ) );
		}

		//center of the box
		for ( int d = 0; d < 2; d++ )
		{
			centBox[ d ] = singleBoxDims[ d ] * 0.5;
		}
		if ( imageSet.bMultiCh )
		{
			centBox[ 2 ] = singleBoxDims[ 3 ] * 0.5;
		}
		else
		{
			centBox[ 2 ] = singleBoxDims[ 2 ] * 0.5;
		}
	}

	IntervalView< T > getBDVIntervalView( IntervalView< T > intervalView )
	{
		IntervalView< T > out;

		if ( !imageSet.bMultiCh )
		{
			//add dimension for the channels
			out = Views.addDimension( intervalView, 0, 0 );
		}
		else
		{
			//change the order of C and Z
			out = Views.permute( intervalView, 2, 3 );
		}

		if ( imageSet.nTimePoints == 1 )
		{
			out = Views.addDimension( out, 0, 0 );
			//test = all_ch_RAI.dimensionsAsLongArray();
			if ( imageSet.nChannels == 1 )
			{
				out = Views.permute( out, 3, 4 );
			}
			//test = all_ch_RAI.dimensionsAsLongArray();
		}
		//finally change C and T (or it can be already fine, if we added C dimension)
		if ( imageSet.nChannels > 1 )
		{
			out = Views.permute( out, 4, 3 );
		}
		return out;

	}

	public void setInitialTransform()
	{
		AffineTransform3D t = new AffineTransform3D();
		t.identity();
		t.scale( globCal[ 0 ], globCal[ 1 ], globCal[ 2 ] );
		viewer.state().setViewerTransform( t );
	}

	public void resetViewXY()
	{

		double scale;
		long[][] nBox;

		nBox = new long[ 2 ][ 3 ];
		nBox[ 1 ][ 0 ] = singleBoxDims[ 0 ] * nCols;
		nBox[ 1 ][ 1 ] = singleBoxDims[ 1 ] * nRows;
		nBox[ 1 ][ 2 ] = singleBoxDims[ 2 ];

		double nW = ( nBox[ 1 ][ 0 ] - nBox[ 0 ][ 0 ] ) * globCal[ 0 ];
		double nH = ( nBox[ 1 ][ 1 ] - nBox[ 0 ][ 1 ] ) * globCal[ 1 ];
		double nWoff = 2.0 * nBox[ 0 ][ 0 ] * globCal[ 0 ];
		double nHoff = 2.0 * nBox[ 0 ][ 1 ] * globCal[ 1 ];
		double nDoff = 2.0 * nBox[ 1 ][ 2 ] * globCal[ 2 ];

		double sW = viewer.getWidth();
		double sH = viewer.getHeight();

		if ( sW / nW < sH / nH )
		{
			scale = sW / nW;
		}
		else
		{
			scale = sH / nH;
		}
		scale = 0.9 * scale;
		AffineTransform3D t = new AffineTransform3D();
		t.identity();

		t.scale( globCal[ 0 ] * scale, globCal[ 1 ] * scale, globCal[ 2 ] * scale );
		t.translate( 0.5 * ( sW - scale * ( nW + nWoff ) ), 0.5 * ( sH - scale * ( nH + nHoff ) ), ( -0.5 ) * scale * ( nDoff ) );

		viewer.state().setViewerTransform( t );

	}

	public void resetViewVolumesXY()
	{
		AffineTransform3D t = new AffineTransform3D();
		t.identity();
		setTransform( t );
	}

	public void resetViewVolumesXZ()
	{
		AffineTransform3D t = new AffineTransform3D();
		t.identity();
		t.rotate( 0, Math.PI / 2.0 );
		setTransform( t );
	}

	public void resetViewVolumesYZ()
	{
		AffineTransform3D t = new AffineTransform3D();
		t.identity();
		t.rotate( 0, Math.PI / 2.0 );
		t.rotate( 1, Math.PI / 2.0 );
		setTransform( t );
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
			affineDragCurrent.rotate( 0, -dY * v );
			affineDragCurrent.rotate( 1, dX * v );
			//affineDragCurrent.rotate( 2, dX * v );

			// center un-shift
			//affineDragCurrent.set( affineDragCurrent.get( 0, 3 ) + oX, 0, 3 );
			//affineDragCurrent.set( affineDragCurrent.get( 1, 3 ) + oY, 1, 3 );
			//System.out.println(Double.toString(oX)+Double.toString(oY));
			//transform.set( affineDragCurrent );
			updateTransform( affineDragCurrent );
		}

		@Override
		public void end( final int x, final int y )
		{}
	}

}
