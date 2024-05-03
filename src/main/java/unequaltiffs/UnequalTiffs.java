/*-
 * #%L
 * Making montage or concatenate tiff images of different sizes
 * %%
 * Copyright (C) 2024 Eugene Katrukha
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package unequaltiffs;

import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.io.DirectoryChooser;
import ij.plugin.PlugIn;
import io.scif.img.ImgIOException;

public class UnequalTiffs< T extends RealType< T > & NativeType< T > > implements PlugIn, WindowListener
{

	final String sPluginVersion = "0.1.1";

	int nImgN;

	int[] ipDim;

	public static final int ALIGN_ZERO = 0, ALIGN_CENTER = 1;

	int nAlignMontage;

	int nAlignConc;

	String sFileExtension = ".tif";

	UTImageSet< T > imageSet = new UTImageSet<>();

	@Override
	public void run( String arg )
	{

		if ( arg == null )
			return;

		DirectoryChooser dc = new DirectoryChooser( "Choose a folder with TIFF images.." );
		String sPath = dc.getDirectory();
		if ( sPath == null )
			return;
		IJ.log( "UnequalTiffCombineMontage v." + sPluginVersion );

		if ( !imageSet.initializeAndCheckConsistensy( sPath, sFileExtension ) )
			return;
		nImgN = imageSet.imgs_in.size();

		//making montage
		if ( arg.equals( "Montage" ) )
		{
			mainMontage();
		}

		//making concatenation
		if ( arg.equals( "Concatenate" ) )
		{
			mainConcatenate();
		}
		boolean bIs2D = false;
		//2D image
		if ( imageSet.sDims.length() < 3 )
		{
			bIs2D = true;
		}
		else
		{
			if ( imageSet.sDims.charAt( 2 ) != 'Z' )
			{
				bIs2D = true;
			}
		}

		if ( arg.equals( "BrowseBDV" ) )
		{
			if ( !bIs2D )
			{
				UTExploreBDV< T > exploreBDV = new UTExploreBDV<>( imageSet );
				exploreBDV.browseBDV();
			}
			else
			{
				IJ.error( "The input is 2D data, BDV/BVV views support only 3D" );
			}
		}
		if ( arg.equals( "BrowseBVV" ) )
		{

			if ( !bIs2D )
			{
				UTExploreBVV< T > exploreBVV = new UTExploreBVV<>( imageSet );
				exploreBVV.browseBVV();
			}
			else
			{
				IJ.error( "The input is 2D data, BDV/BVV views support only 3D" );
			}
		}
	}

	void mainMontage()
	{
		//Calculate the number of rows and columns
		//Based on MontageMaker, this tries to make the
		//montage as square as possible

		int nCols = ( int ) Math.sqrt( nImgN );
		int nRows = nCols;
		int n = nImgN - nCols * nRows;
		if ( n > 0 )
			nCols += ( int ) Math.ceil( ( double ) n / nRows );
		final String[] sAlingMontage = new String[ 2 ];
		sAlingMontage[ 0 ] = "Zero (origin)";
		sAlingMontage[ 1 ] = "Center";

		final GenericDialog gdMontage = new GenericDialog( "Make montage" );
		gdMontage.addNumericField( "number of columns:", nCols, 0 );
		gdMontage.addNumericField( "number of rows:", nRows, 0 );
		gdMontage.addChoice( "Align images by:", sAlingMontage, Prefs.get( "UnequalTiffs.nAlignMontage", sAlingMontage[ ALIGN_ZERO ] ) );
		gdMontage.showDialog();
		if ( gdMontage.wasCanceled() )
			return;
		nCols = ( int ) gdMontage.getNextNumber();
		nRows = ( int ) gdMontage.getNextNumber();
		nAlignMontage = gdMontage.getNextChoiceIndex();
		Prefs.set( "UnequalTiffs.nAlignMontage", sAlingMontage[ nAlignMontage ] );
		n = nImgN - nCols * nRows;
		if ( n > 0 )
			nCols += ( int ) Math.ceil( ( double ) n / nRows );
		IJ.log( "Making montage with " + Integer.toString( nRows ) + " rows and " + Integer.toString( nCols ) + " columns." );

		IJ.log( "Align images by:" + sAlingMontage[ nAlignMontage ] + "." );

		UTMontage< T > utM = new UTMontage<>( imageSet );
		Img< T > img_montage = utM.makeMontage( nRows, nCols, nAlignMontage );
		ImagePlus ip_montage;

		ip_montage = ImageJFunctions.show( prepareMontageForImageJView( img_montage ), "Montage" );
		ip_montage.setCalibration( imageSet.cal );
		utM.addCaptionsOverlay( imageSet.sFileNamesShort, ip_montage );
		ip_montage.getWindow().addWindowListener( this );

		IJ.log( "Done." );
	}

	void mainConcatenate()
	{
		if ( imageSet.sDims.length() >= 5 )
		{
			IJ.error( "Images already are XYZTC, no free dimension to concatenate.\n Aborting." );
			return;
		}
		String sConcatDim = "";
		String sConcatOptions = "";

		//no need to ask for the dimension
		if ( imageSet.sDims.length() == 4 )
		{
			if ( imageSet.nChannels == 1 )
			{
				sConcatDim = "C";
				IJ.log( "Concatenating along channel axis." );
			}
			if ( imageSet.nTimePoints == 1 )
			{
				sConcatDim = "T";
				IJ.log( "Concatenating along time." );
			}
			if ( imageSet.nSlices == 1 )
			{
				sConcatDim = "Z";
				IJ.log( "Concatenating along Z axis." );
			}
		}
		else
		{
			if ( imageSet.nSlices == 1 )
			{
				sConcatOptions = sConcatOptions + "Z";
			}
			if ( imageSet.nTimePoints == 1 )
			{
				sConcatOptions = sConcatOptions + "T";
			}
			if ( imageSet.nChannels == 1 )
			{
				sConcatOptions = sConcatOptions + "C";
			}
		}
		//ask user, what he wants as a concatenation axis
		final GenericDialog gdConcat = new GenericDialog( "Concatenate" );
		final String[] sAlingConc = new String[ 2 ];
		String[] sConcatDimDial = new String[ 1 ];
		if ( sConcatOptions.length() > 0 )
		{
			sConcatDimDial = new String[ sConcatOptions.length() ];
			for ( int i = 0; i < sConcatOptions.length(); i++ )
			{
				sConcatDimDial[ i ] = sConcatOptions.substring( i, i + 1 );
			}
			gdConcat.addChoice( "Concatenate along axis:", sConcatDimDial, sConcatDimDial[ 0 ] );
		}

		sAlingConc[ 0 ] = "Zero (origin)";
		sAlingConc[ 1 ] = "Center";
		gdConcat.addChoice( "Align images by:", sAlingConc, Prefs.get( "UnequalTiffs.nAlignConc", sAlingConc[ ALIGN_ZERO ] ) );
		gdConcat.showDialog();
		if ( gdConcat.wasCanceled() )
			return;
		sConcatDim = "";
		if ( sConcatOptions.length() > 0 )
		{
			sConcatDim = sConcatDimDial[ gdConcat.getNextChoiceIndex() ];
			switch ( sConcatDim )
			{
			case "C":
				IJ.log( "Concatenating along channel axis." );
				break;
			case "T":
				IJ.log( "Concatenating along time." );
				break;
			case "Z":
				IJ.log( "Concatenating along Z axis." );
				break;

			}
		}
		nAlignConc = gdConcat.getNextChoiceIndex();
		Prefs.set( "UnequalTiffs.nAlignConc", sAlingConc[ nAlignConc ] );
		IJ.log( "Align images by:" + sAlingConc[ nAlignConc ] + "." );
		UTConcatenate< T > utC = new UTConcatenate<>( imageSet );
		Img< T > img_conc = utC.concatenate( nAlignConc );
		ImagePlus ip_conc = null;

		ip_conc = ImageJFunctions.show( prepareConcatForImageJView( img_conc, sConcatDim ), "Concatenated" );

		ip_conc.setCalibration( imageSet.cal );
		utC.addCaptionsOverlay( imageSet.sFileNamesShort, ip_conc, sConcatDim );
		ip_conc.getWindow().addWindowListener( this );
		IJ.log( "Done." );
	}

	/** function orders input Img<T> to ImageJ order of XYCZT **/
	public IntervalView< T > prepareMontageForImageJView( final Img< T > img_montage )
	{
		IntervalView< T > out = Views.interval( img_montage, img_montage );
		if ( !imageSet.bMultiCh )
		{
			//so it looks a bit better
			out = Views.permute( Views.addDimension( out, 0, 0 ), 2, out.numDimensions() );
		}
		if ( imageSet.nTimePoints > 1 && imageSet.nSlices == 1 )
		{
			out = Views.permute( Views.addDimension( out, 0, 0 ), out.numDimensions() - 1, out.numDimensions() );
		}

		return out;
	}

	/** function orders input Img<T> to ImageJ order of XYCZT **/
	public IntervalView< T > prepareConcatForImageJView( final Img< T > img_concat, String sConcatDim )
	{
		IntervalView< T > out = Views.interval( img_concat, img_concat );
		switch ( sConcatDim )
		{
		case "T":
			if ( img_concat.numDimensions() < 5 )
			{
				if ( !imageSet.bMultiCh )
				{
					out = Views.permute( Views.addDimension( out, 0, 0 ), 2, out.numDimensions() );
				}
				if ( imageSet.nSlices == 1 )
				{
					out = Views.addDimension( out, 0, 0 );
				}
				out = Views.permute( out, 3, 4 );
			}
			break;
		case "C":
			out = Views.permute( out, 2, out.numDimensions() - 1 );
			if ( imageSet.nSlices == 1 )
			{
				out = Views.addDimension( out, 0, 0 );
				out = Views.permute( out, 3, 4 );
			}
			break;
		case "Z":
			if ( imageSet.bMultiCh )
			{
				out = Views.permute( out, 3, out.numDimensions() - 1 );
			}
			else
			{
				out = Views.permute( out, 2, out.numDimensions() - 1 );
			}
			break;

		}
		return out;
	}

	/** Helper function for the overlays **/
	public static String getTruncatedString( final FontMetrics fM, final Graphics g, final int nMaxWidth, final String sIn )
	{
		if ( sIn == null )
			return null;

		String truncated = sIn;
		int length = sIn.length();
		while ( length > 0 )
		{
			if ( fM.getStringBounds( truncated, g ).getWidth() <= nMaxWidth )
			{
				return truncated;
			}
			length--;
			truncated = sIn.substring( 0, length );
		}
		return "";
	}

	@Override
	public void windowOpened( WindowEvent e )
	{

	}

	@Override
	public void windowClosing( WindowEvent e )
	{

	}

	@Override
	public void windowClosed( WindowEvent e )
	{
		if ( imageSet.filesOpened != null )
		{
			for ( int i = 0; i < imageSet.filesOpened.size(); i++ )
			{
				imageSet.filesOpened.get( i ).dispose();
			}
		}

	}

	@Override
	public void windowIconified( WindowEvent e )
	{

	}

	@Override
	public void windowDeiconified( WindowEvent e )
	{

	}

	@Override
	public void windowActivated( WindowEvent e )
	{

	}

	@Override
	public void windowDeactivated( WindowEvent e )
	{

	}

	public static void main( final String[] args ) throws ImgIOException, IncompatibleTypeException
	{
		// open an ImageJ window
		new ImageJ();
		@SuppressWarnings( "rawtypes" )
		UnequalTiffs un = new UnequalTiffs();
		//un.run("Montage");
		//un.run( "Concatenate" );
		//un.run("BrowseBDV");
		un.run("BrowseBVV");


	}

}
