package unequaltiffs;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
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

import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.TextRoi;
import ij.gui.Toolbar;

public class UTConcatenate< T extends RealType< T > & NativeType< T > >
{

	UTImageSet< T > imageSet;

	final ArrayList< IntervalView< T > > intervals;

	final long[] singleBoxDims;

	final int nImgN;

	public UTConcatenate( final UTImageSet< T > imageSet_ )
	{
		imageSet = imageSet_;
		singleBoxDims = imageSet.getSingleBoxDims();
		intervals = new ArrayList<>();
		nImgN = imageSet.im_dims.size();
	}

	public Img< T > concatenate( final int nAlignConc )
	{

		long[][] singleBoxInterval = new long[ 2 ][ singleBoxDims.length ];
		for ( int d = 0; d < singleBoxDims.length; d++ )
		{
			singleBoxInterval[ 1 ][ d ] = singleBoxDims[ d ] - 1;
		}

		//align images
		if ( nAlignConc == UnequalTiffs.ALIGN_ZERO )
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

		final long[] dimensions = new long[ imageSet.nDimN + 1 ];
		for ( int d = 0; d < imageSet.nDimN; d++ )
		{
			dimensions[ d ] = singleBoxDims[ d ];
		}
		dimensions[ imageSet.nDimN ] = nImgN;

		final int[] cellDimensions = new int[] { ( int ) singleBoxDims[ 0 ], ( int ) singleBoxDims[ 1 ], 1 };
		final CellLoader< T > loader = new CellLoader< T >()
		{
			@Override
			public void load( final SingleCellArrayImg< T, ? > cell ) throws Exception
			{
				final int ind = ( int ) cell.min( imageSet.nDimN );
				IntervalView< T > slice = intervals.get( ind );
				IntervalView< T > sliceCell = Views.hyperSlice( cell, imageSet.nDimN, ind );
				for ( int d = imageSet.nDimN - 1; d >= 2; d-- )
				{
					slice = Views.hyperSlice( slice, d, ( int ) cell.min( d ) );
					sliceCell = Views.hyperSlice( sliceCell, d, ( int ) cell.min( d ) );
				}
				LoopBuilder.setImages( slice, sliceCell ).forEachPixel( ( s, t ) -> t.set( s ) );

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

	/** function adding filenames to overlay of Concatenate **/
	public void addCaptionsOverlay( final String[] filenames, final ImagePlus im, final String sConcDimension )
	{

		Overlay imOverlay = new Overlay();
		final Font font = new Font( TextRoi.getDefaultFontName(), TextRoi.getDefaultFontStyle(), TextRoi.getDefaultFontSize() );

		final Graphics g = ( new BufferedImage( 1, 1, BufferedImage.TYPE_INT_RGB ) ).createGraphics();
		g.setFont( font );
		final FontMetrics fM = g.getFontMetrics();

		TextRoi txtROI;
		Rectangle2D.Double bounds;

		//for(int i = 0; i<im_dims.size();i++)
		for ( int i = 0; i < nImgN; i++ )
		{
			//String in = getTruncatedString( fM, g, (int) singleBox[1][0]-5, filenames[i]);
			txtROI = new TextRoi( 5, 5, UnequalTiffs.getTruncatedString( fM, g, ( int ) singleBoxDims[ 0 ] - 5, filenames[ i ] ), font );
			txtROI.setStrokeColor( Toolbar.getForegroundColor() );
			txtROI.setAntiAlias( TextRoi.isAntialiased() );
			txtROI.setJustification( TextRoi.getGlobalJustification() );
			bounds = txtROI.getFloatBounds();
			bounds.width = 5;
			txtROI.setBounds( bounds );
			switch ( sConcDimension )
			{
			case "T":
				txtROI.setPosition( 1, 1, i + 1 );
				break;
			case "C":
				txtROI.setPosition( i + 1, 1, 1 );
				break;
			case "Z":
				txtROI.setPosition( 1, i + 1, 1 );
				break;
			}

			imOverlay.add( txtROI );

		}

		im.setOverlay( imOverlay );
	}
}
