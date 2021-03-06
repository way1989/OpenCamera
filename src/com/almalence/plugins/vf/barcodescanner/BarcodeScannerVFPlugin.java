package com.almalence.plugins.vf.barcodescanner;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.RelativeLayout;

import com.almalence.opencam.MainScreen;
import com.almalence.opencam.Plugin;
import com.almalence.opencam.PluginManager;
import com.almalence.opencam.PluginViewfinder;
import com.almalence.opencam.R;
import com.almalence.opencam.SoundPlayer;
import com.almalence.ui.RotateImageView;
import com.almalence.util.ImageConversion;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

public class BarcodeScannerVFPlugin extends PluginViewfinder {
    
	private static final double BOUNDS_FRACTION = 0.6;
	private final static Boolean ON = true;
	private final static Boolean OFF = false;
	  
	private final MultiFormatReader mMultiFormatReader = new MultiFormatReader();
	private SoundPlayer mSoundPlayer = null;
	public static Boolean mBarcodeScannerState = OFF;
	private int mFrameCounter = 0;
	private BoundingView mBound = null;
	private RotateImageView mBarcodesListButton;
	private View mButtonsLayout;
	private BarcodeHistoryListDialog barcodeHistoryDialog;
	private BarcodeViewDialog barcodeViewDialog;
	
	public BarcodeScannerVFPlugin()
	{
		super("com.almalence.plugins.barcodescannervf",
			  R.xml.preferences_vf_barcodescanner,
			  0,
			  R.drawable.gui_almalence_settings_scene_barcode_on,
			  "Barcode scanner");
	}
	
	@Override
	public void onResume() {
		updatePreferences();
	}
	
	void updatePreferences()
	{
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(MainScreen.mainContext);
		mBarcodeScannerState = prefs.getBoolean("PrefBarcodescannerVF", false);
		
		if (mBarcodeScannerState == ON) {
			quickControlIconID = R.drawable.gui_almalence_settings_scene_barcode_on;
		} else {
			quickControlIconID = R.drawable.gui_almalence_settings_off_barcode_scanner;
		}
		
        showGUI();
	}
	
	@Override
    public void onOrientationChanged(int orientation) {
		if (mBarcodesListButton != null) {
			mBarcodesListButton.setOrientation(MainScreen.guiManager.getLayoutOrientation());
			mBarcodesListButton.invalidate();
			mBarcodesListButton.requestLayout();    			
		}
		if (barcodeHistoryDialog != null) {
			barcodeHistoryDialog.setRotate(MainScreen.guiManager.getLayoutOrientation());
		}
		if (barcodeViewDialog != null) {
			barcodeViewDialog.setRotate(MainScreen.guiManager.getLayoutOrientation());
		}
    }
	
	@Override
	public void onQuickControlClick()
	{
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(MainScreen.mainContext);
		Editor editor = prefs.edit();
		
		if (mBarcodeScannerState == ON) {
			quickControlIconID = R.drawable.gui_almalence_settings_off_barcode_scanner;
        	editor.putBoolean("PrefBarcodescannerVF", false);
		} else {
			quickControlIconID = R.drawable.gui_almalence_settings_scene_barcode_on;
        	editor.putBoolean("PrefBarcodescannerVF", true);
		}
        editor.commit();
        
        updatePreferences();
	}
	
	/**
     * Show or hide GUI elements of plugin. Depends on plugin state and history.
     */
	public void showGUI () {
		if (mBarcodeScannerState == ON) {
			if (mBound == null) {
				createBoundView();
			}
			if (mBarcodesListButton == null) {
				createScreenButton();
			}
			
			if (mBound != null) {
				mBound.setVisibility(View.VISIBLE);
			}
			if (mBarcodesListButton != null) {
				if (BarcodeStorageHelper.getBarcodesList() != null && BarcodeStorageHelper.getBarcodesList().size() > 0) {
					mBarcodesListButton.setVisibility(View.VISIBLE);
				} else {
					mBarcodesListButton.setVisibility(View.GONE);
				}
			}
		} else {
			if (mBound != null) {
				mBound.setVisibility(View.GONE);
			}
			if (mBarcodesListButton != null) {
				mBarcodesListButton.setVisibility(View.GONE);
			}
		}
	}
	
	public void initializeSoundPlayer() {
        mSoundPlayer = new SoundPlayer(MainScreen.mainContext, MainScreen.mainContext.getResources().openRawResourceFd(R.raw.plugin_vf_focus_ok));
    }
	
	public void releaseSoundPlayer() {
        if (mSoundPlayer != null) {
        	mSoundPlayer.release();
        	mSoundPlayer = null;
        }
    }
	
	@Override
	public void onCameraParametersSetup() {
		initializeSoundPlayer();
	}
	
	@Override
	public void onPause() {
		releaseSoundPlayer();
	}
	
	@Override
	public void onGUICreate() {
		clearViews();
		createBoundView();
		createScreenButton();
		showGUI();
	}
	
	/**
	 * Create bound view.
	 */
	public void createBoundView() {
		if (mBound != null) {
			return;
		}
		Camera camera = MainScreen.thiz.getCamera();
    	if (null==camera) {
    		return;
    	}
		
		mBound = new BoundingView(MainScreen.mainContext);
		mBound.setVisibility(View.VISIBLE);
		
		RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
		((RelativeLayout)MainScreen.thiz.findViewById(R.id.specialPluginsLayout)).addView(mBound, params);
		
		mBound.setLayoutParams(params);
		
		((RelativeLayout)MainScreen.thiz.findViewById(R.id.specialPluginsLayout)).requestLayout();
	}
	
	
	/**
	 *  Create history button.
	 */
	public void createScreenButton()
	{
		LayoutInflater inflator = MainScreen.thiz.getLayoutInflater();
		mButtonsLayout = inflator.inflate(R.layout.plugin_vf_barcodescanner_layout, null, false);
		mButtonsLayout.setVisibility(View.VISIBLE);
		
		mBarcodesListButton = (RotateImageView) mButtonsLayout.findViewById(R.id.buttonBarcodesList);
	
		List<View> specialView = new ArrayList<View>();
		RelativeLayout specialLayout = (RelativeLayout)MainScreen.thiz.findViewById(R.id.specialPluginsLayout3);
		for(int i = 0; i < specialLayout.getChildCount(); i++)
			specialView.add(specialLayout.getChildAt(i));

		for(int j = 0; j < specialView.size(); j++)
		{
			View view = specialView.get(j);
			int view_id = view.getId();
			int layout_id = mButtonsLayout.getId();
			if(view_id == layout_id)
			{
				if(view.getParent() != null)
					((ViewGroup)view.getParent()).removeView(view);
				
				specialLayout.removeView(view);
			}
		}
		
		mBarcodesListButton.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				showBarcodesHistoryDialog();
			}
			
		});
		
		RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
		params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
		
		((RelativeLayout)MainScreen.thiz.findViewById(R.id.specialPluginsLayout3)).addView(mButtonsLayout, params);
		
		mButtonsLayout.setLayoutParams(params);
		mButtonsLayout.requestLayout();
		
		((RelativeLayout)MainScreen.thiz.findViewById(R.id.specialPluginsLayout3)).requestLayout();
		
		mBarcodesListButton.setOrientation(MainScreen.guiManager.getLayoutOrientation());
		mBarcodesListButton.invalidate();
		mBarcodesListButton.requestLayout();
	}
	
	protected void showBarcodesHistoryDialog() {
		barcodeHistoryDialog = new BarcodeHistoryListDialog(MainScreen.thiz);

		barcodeHistoryDialog.list.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				Barcode barcode = (Barcode) barcodeHistoryDialog.list.getAdapter().getItem(position);
				showBarcodeViewDialog(barcode);
			}
		});
		
		barcodeHistoryDialog.setOnDismissListener(new OnDismissListener() {
			public void onDismiss(DialogInterface dialog) {
				mBarcodeScannerState = ON;
				showGUI();
			}
		});
		mBarcodeScannerState = OFF;

		barcodeHistoryDialog.show();
	}

	@Override
	public void onPreviewFrame(byte[] data, Camera paramCamera) {
		if (mBarcodeScannerState == OFF)
			return;
		mFrameCounter++;
		if (mFrameCounter != 10) {
			return;
		}

		Camera.Parameters params = MainScreen.thiz.getCameraParameters();
		if (params == null)
			return;

		int previewWidth = params.getPreviewSize().width;
		int previewHeight = params.getPreviewSize().height;

        new DecodeAsyncTask(previewWidth, previewHeight).execute(data);
        
		mFrameCounter = 0;
	}
	
    public synchronized PlanarYUVLuminanceSource buildLuminanceSource(byte[] data, int width, int height, Rect boundingRect) {
    	if (boundingRect == null || data == null) {
    		return null;
    	}
    	return new PlanarYUVLuminanceSource(data, width, height, boundingRect.left, boundingRect.top,
                boundingRect.width(), boundingRect.height(), false);
    }
	
    /**
     *  Handle success decoded barcode.
     * @param barcode
     */
	public void onDecoded(Barcode barcode) {
		if (mBarcodeScannerState == OFF) {
			return;
		}
		
		//sale hook
		if (barcode.getData().equals("abc.almalence.com/qrpromo") && !MainScreen.thiz.isUnlockedAll())
		{
			MainScreen.guiManager.showStore(true);
			return;
		}
		
        BarcodeStorageHelper.addBarcode(barcode);
        
        showBarcodeViewDialog(barcode);
        
        if (mSoundPlayer != null)                
        	if (!MainScreen.ShutterPreference)
        		mSoundPlayer.play();
    }
	
	protected void showBarcodeViewDialog(Barcode barcode) {
    	barcodeViewDialog = new BarcodeViewDialog(MainScreen.thiz, barcode);
    	
    	showGUI();
    	
    	barcodeViewDialog.setOnDismissListener(new OnDismissListener() {
			public void onDismiss(DialogInterface dialog) {
				mBarcodeScannerState = ON;
			}
		});
		
		mBarcodeScannerState = OFF;
		barcodeViewDialog.show();
    }
	
	/**
     * @return bounding rect for camera
     */
    public final synchronized Rect getBoundingRect() {
    	Camera.Parameters params = MainScreen.thiz.getCameraParameters();
        if (params != null) {
            Camera.Size previewSize = params.getPreviewSize();
            int previewHeight = previewSize.height;
            int previewWidth = previewSize.width;

            double heightFraction = BOUNDS_FRACTION;
            double widthFraction = BOUNDS_FRACTION;

            int height = (int) (previewHeight * heightFraction);
            int width = (int) (previewWidth * widthFraction);
            int left = (int) (previewWidth * ((1 - widthFraction) / 2));
            int top = (int) (previewHeight * ((1 - heightFraction) / 2));
            int right = left + width;
            int bottom = top + height;

            return new Rect(left, top, right, bottom);
        }
        return null;
    }
	
	/**
     * @return bounding rect for ui
     */
    public final synchronized Rect getBoundingRectUi(int uiWidth, int uiHeight) {
        double heightFraction = BOUNDS_FRACTION;
        double widthFraction = BOUNDS_FRACTION;

        int height = (int) (uiHeight * heightFraction);
        int width = (int) (uiWidth * widthFraction);
        int left = (int) (uiWidth * ((1 - widthFraction) / 2));
        int top = (int) (uiHeight * ((1 - heightFraction) / 2));
        int right = left + width;
        int bottom = top + height;

        return new Rect(left, top, right, bottom);
    }
    
    /**
	 * Asynchronous task for decoding and finding barcode
	 */
	private class DecodeAsyncTask extends AsyncTask<byte[], Void, Barcode> {
	    private int width;
	    private int height;
	    
	    private DecodeAsyncTask(int width, int height) {
	        this.width = width;
	        this.height = height;
	    }
	    
	    @Override
	    protected Barcode doInBackground(byte[]... datas) {
	        Result rawResult = null;
	        File file = null;
	        final PlanarYUVLuminanceSource source = buildLuminanceSource(datas[0], width,
                            height, getBoundingRect());
	        if (source != null) {
	            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
	            try {
	                rawResult = mMultiFormatReader.decodeWithState(bitmap);
	            } catch (ReaderException re) {
	                // nothing to do here
	            } finally {
	                mMultiFormatReader.reset();
	            }
	        }
	        
	        if (rawResult == null) {
	        	return null;
	        }
	        
			if(rawResult != null) {
				file = saveDecodedImageToFile(datas);
			}
	        
			Barcode barcode = null;
			if (file != null) {
				barcode = new Barcode(rawResult, file.getPath());
			} else {
				barcode = new Barcode(rawResult);
			}
			
	        return barcode;
	    }
	    
	    @Override
        protected void onPostExecute(Barcode barcode) {
            if (barcode != null) {
                onDecoded(barcode);
            }
        }
	}
	
	private synchronized File saveDecodedImageToFile(byte[]... datas) {
		File file = null;
		Camera.Parameters params = MainScreen.thiz.getCameraParameters();			
		int imageWidth = params.getPreviewSize().width;
		int imageHeight = params.getPreviewSize().height;
		
		byte[] dataRotated = new byte[datas[0].length];
		ImageConversion.TransformNV21(datas[0], dataRotated, imageWidth, imageHeight, 0, 0, 1);
		datas[0] = dataRotated;
		
		
		Rect rect = new Rect(0, 0, MainScreen.previewHeight, MainScreen.previewWidth); 
        YuvImage img = new YuvImage(datas[0], ImageFormat.NV21, MainScreen.previewHeight, MainScreen.previewWidth, null);
        OutputStream outStream = null;
        
        Calendar d = Calendar.getInstance();
        String fileFormat = String.format("%04d%02d%02d_%02d%02d%02d",
        		d.get(Calendar.YEAR),
        		d.get(Calendar.MONTH)+1,
        		d.get(Calendar.DAY_OF_MONTH),
        		d.get(Calendar.HOUR_OF_DAY),
        		d.get(Calendar.MINUTE),
        		d.get(Calendar.SECOND));
        
        File saveDir = PluginManager.getInstance().GetSaveDir(false);
        file = new File(saveDir, "QR_" + fileFormat + ".jpg");
        FileOutputStream os = null;
        try {
        	os = new FileOutputStream(file);
    	}
    	catch (Exception e) {
    		//save always if not working saving to sdcard
        	e.printStackTrace();
        	saveDir = PluginManager.getInstance().GetSaveDir(true);
        	file = new File(saveDir, fileFormat+".jpg");
        	try {
				os = new FileOutputStream(file);
			} catch (FileNotFoundException e1) {
				e1.printStackTrace();
			}
        }
        
        if (os != null) {
        	try {
	            outStream = new FileOutputStream(file);
	            img.compressToJpeg(rect, 100, outStream);
	            outStream.flush();
	            outStream.close();
	        } 
	        catch (FileNotFoundException e) {
	            e.printStackTrace();
	        }
	        catch (IOException e) {
	            e.printStackTrace();
	        }	
        }
        
        return file;
	}
    
    /**
     * View for displaying bounds for active camera region
     */
    class BoundingView extends View {
            public BoundingView(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setARGB(110, 128, 128, 128);
            
            int width = canvas.getWidth();
            int height = canvas.getHeight();
            Rect boundingRect = getBoundingRectUi(canvas.getWidth(), canvas.getHeight());
            
            
            canvas.drawRect(0, 0, width, boundingRect.top, paint);
            canvas.drawRect(0, boundingRect.top, boundingRect.left, boundingRect.bottom + 1, paint);
            canvas.drawRect(boundingRect.right + 1, boundingRect.top, width, boundingRect.bottom + 1, paint);
            canvas.drawRect(0, boundingRect.bottom + 1, width, height, paint);
            super.onDraw(canvas);
        }
    }
    
    
}
