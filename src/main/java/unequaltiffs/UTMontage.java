package unequaltiffs;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccess;
import net.imglib2.cache.img.CellLoader;
import net.imglib2.cache.img.ReadOnlyCachedCellImgFactory;
import net.imglib2.cache.img.ReadOnlyCachedCellImgOptions;
import net.imglib2.cache.img.SingleCellArrayImg;
import net.imglib2.cache.img.optional.CacheOptions.CacheType;
import net.imglib2.img.Img;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.TextRoi;
import ij.gui.Toolbar;

public class UTMontage< T extends RealType< T > & NativeType< T > >
{

	UTImageSet< T > imageSet;

	final ArrayList< IntervalView< T > > intervals;

	int nCols;

	int nRows;

	int[][] indexes;

	final long[] singleBoxDims;

	final int nImgN;

	int nAlignMontage;

	public UTMontage( final UTImageSet< T > imageSet_ )
	{
		imageSet = imageSet_;
		singleBoxDims = imageSet.getSingleBoxDims();
		intervals = new ArrayList<>();
		nImgN = imageSet.im_dims.size();
	}

	public Img< T > makeMontage( final int nRows_, final int nCols_, final int nAlignMontage_ )
	{
		nCols = nCols_;
		nRows = nRows_;
		nAlignMontage = nAlignMontage_;

		//calculate the range of boxes size
		indexes = new int[ nCols ][ nRows ];
		int nR = 0;
		int nC = 0;
		//for(int i = 0; i<im_dims.size();i++)
		for ( int i = 0; i < nCols * nRows; i++ )
		{
			if ( i < nImgN )
			{
				indexes[ nC ][ nR ] = i;
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
		long[][] singleBoxInterval = new long[ 2 ][ singleBoxDims.length ];
		for ( int d = 0; d < singleBoxDims.length; d++ )
		{
			singleBoxInterval[ 1 ][ d ] = singleBoxDims[ d ] - 1;
		}
		if ( nAlignMontage == UnequalTiffs.ALIGN_ZERO )
		{
			for ( int i = 0; i < imageSet.im_dims.size(); i++ )
			{
				intervals.add( Views.interval( Views.extendZero( imageSet.imgs_in.get( i ) ),
						new FinalInterval( singleBoxInterval[ 0 ], singleBoxInterval[ 1 ] ) ) );
			}
		}
		else
		{
			long[] nShifts = new long[ imageSet.nDimN ];
			for ( int i = 0; i < imageSet.im_dims.size(); i++ )
			{
				for ( int d = 0; d < imageSet.nDimN; d++ )
				{
					nShifts[ d ] = ( int ) Math.floor( 0.5 * ( singleBoxDims[ d ] - imageSet.im_dims.get( i )[ d ] ) );
				}

				intervals.add( Views.interval(
						Views.translate(
								Views.extendZero( imageSet.imgs_in.get( i ) ), nShifts ),
						new FinalInterval( singleBoxInterval[ 0 ], singleBoxInterval[ 1 ] ) ) );
			}
		}

		final long[] dimensions = new long[ imageSet.nDimN ];
		dimensions[ 0 ] = nCols * ( singleBoxDims[ 0 ] );
		dimensions[ 1 ] = nRows * ( singleBoxDims[ 1 ] );
		if ( imageSet.nDimN > 2 )
		{
			for ( int d = 2; d < imageSet.nDimN; d++ )
			{
				dimensions[ d ] = ( singleBoxDims[ d ] );
			}
		}
		final int[] cellDimensions;
		if ( imageSet.bMultiCh )
		{
			cellDimensions = new int[] { ( int ) ( singleBoxDims[ 0 ] ), ( int ) ( singleBoxDims[ 1 ] ), ( int ) ( singleBoxDims[ 2 ] ) };
		}
		else
		{
			cellDimensions = new int[] { ( int ) singleBoxDims[ 0 ], ( int ) singleBoxDims[ 1 ], 1 };
		}
		final CellLoader< T > loader = new CellLoader< T >()
		{
			@Override
			public void load( final SingleCellArrayImg< T, ? > cell ) throws Exception
			{
				final int x = ( int ) cell.min( 0 );
				final int y = ( int ) cell.min( 1 );
				final int nCol = Math.round( x / singleBoxDims[ 0 ] );
				final int nRow = Math.round( y / singleBoxDims[ 1 ] );
				final int imInd = indexes[ nCol ][ nRow ];

				//empty cell
				if ( imInd < 0 )
					return;
				final Cursor< T > curCell = cell.localizingCursor();
				final RandomAccess< T > ra = intervals.get( imInd ).randomAccess();

				long[] pos = new long[ imageSet.nDimN ];
				while ( curCell.hasNext() )
				{
					curCell.fwd();
					curCell.localize( pos );
					pos[ 0 ] -= x;
					pos[ 1 ] -= y;
					ra.setPosition( pos );
					curCell.get().set( ra.get() );
				}
			}

		};

		Cursor< T > cursorTest = imageSet.imgs_in.get( 0 ).cursor();
		cursorTest.fwd();
		return new ReadOnlyCachedCellImgFactory().create(
				dimensions,
				cursorTest.get(),
				loader,
				ReadOnlyCachedCellImgOptions.options().cellDimensions( cellDimensions ).cacheType( CacheType.BOUNDED ).maxCacheSize( 3 ) );

	}

	/** function adding filenames to overlay of Montage **/
	public void addCaptionsOverlay( final String[] filenames, final ImagePlus im )
	{

		Overlay imOverlay = new Overlay();
		final Font font = new Font( TextRoi.getDefaultFontName(), TextRoi.getDefaultFontStyle(), TextRoi.getDefaultFontSize() );

		final Graphics g = ( new BufferedImage( 1, 1, BufferedImage.TYPE_INT_RGB ) ).createGraphics();
		g.setFont( font );
		final FontMetrics fM = g.getFontMetrics();

		TextRoi txtROI;
		Rectangle2D.Double bounds;
		int nR = 0;
		int nC = 0;
		//for(int i = 0; i<im_dims.size();i++)
		for ( int i = 0; i < nImgN; i++ )
		{
			//String in = getTruncatedString( fM, g, (int) singleBox[1][0]-5, filenames[i]);
			txtROI = new TextRoi( 5 + nC * singleBoxDims[ 0 ], 5 + nR * singleBoxDims[ 1 ],
					UnequalTiffs.getTruncatedString( fM, g, ( int ) singleBoxDims[ 0 ] - 5, filenames[ i ] ), font );
			txtROI.setStrokeColor( Toolbar.getForegroundColor() );
			txtROI.setAntiAlias( TextRoi.isAntialiased() );
			txtROI.setJustification( TextRoi.getGlobalJustification() );
			bounds = txtROI.getFloatBounds();
			bounds.width = 5;
			txtROI.setBounds( bounds );

//			if(bounds.width>singleBox[1][0])
//			{
//				bounds.width = (double)singleBox[1][0];
//				txtROI.setBounds(bounds);
//			}

			imOverlay.add( txtROI );

			nC++;
			if ( nC >= nCols )
			{
				nC = 0;
				nR++;
			}
		}

		im.setOverlay( imOverlay );

	}

}
