package com.cclink.obbdownloader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.android.vending.expansion.downloader.Helpers;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.view.WindowManager;

public class ObbUnzipHelper {

	private Context mContext;
	private ObbUnzipListener mListener;
	private UnzipProgressDialog mUnzipProgressDlg;
	private XAPKFile[] xAPKS;
	
	public ObbUnzipHelper(Context context, ObbInfo obbInfo) {
        mContext = context;
        int mainVer = obbInfo.getMainObbVersion();
        long mainSize = obbInfo.getMainObbFileSize();
        int patchVer = obbInfo.getPatchObbVersion();
        long patchSize = obbInfo.getPatchObbFileSize();
        if (mainVer > 0 && patchVer > 0) {
            if (mainSize > 0 && patchSize > 0) {
                xAPKS = new XAPKFile[2];
                xAPKS[0] = new XAPKFile(true, mainVer, mainSize);
                xAPKS[1] = new XAPKFile(false, patchVer, patchSize);
            } else {
                xAPKS = new XAPKFile[1];
                xAPKS[0] = new XAPKFile(true, mainVer, mainSize);
            }
        } else {
            xAPKS = new XAPKFile[0];
        }
    }
	
	private XAPKFile[] getAllXAPKs() {
		return xAPKS;
	}
	
	private XAPKFile[] getMainXAPKs() {
		for (XAPKFile xf : xAPKS) {
			if (xf.mIsMain) {
				XAPKFile[] xfs = new XAPKFile[1];
				xfs[0] = xf;
				return xfs;
			}
		}
		XAPKFile[] xfs = new XAPKFile[0];
		return xfs;
	}
	
	private XAPKFile[] getPatchXAPKs() {
		for (XAPKFile xf : xAPKS) {
			if (!xf.mIsMain) {
				XAPKFile[] xfs = new XAPKFile[1];
				xfs[0] = xf;
				return xfs;
			}
		}
		XAPKFile[] xfs = new XAPKFile[0];
		return xfs;
	}
	
	private boolean checkXAPKs(XAPKFile[] xfs) {
		if (xfs.length == 0) {
    		Log.i("APKExpansionUnzip", "Unzip failed: No expansion files");
    		return false;
		}
    	for (XAPKFile xf : xfs) {
            String fileName = Helpers.getExpansionAPKFileName(mContext, xf.mIsMain, xf.mFileVersion);
            if (!Helpers.doesFileExist(mContext, fileName, xf.mFileSize, false)) {
                if (xf.mIsMain) {
                    Log.i("APKExpansionUnzip", "Unzip failed: Main expansion file does not exist");
                } else {
                    Log.i("APKExpansionUnzip", "Unzip failed: Patch expansion file does not exist");
                }
                return false;
            }
        }
    	return true;
	}
	
	public void unzipAllToFolder(Context context, String folder, ObbUnzipListener listener) {
		XAPKFile[] xfs = getAllXAPKs();
		unzip(xfs, folder, listener);
    }
    
    public void unzipMainobbToFolder(Context context, String folder, ObbUnzipListener listener) {
    	XAPKFile[] xfs = getMainXAPKs();
    	unzip(xfs, folder, listener);
    }
    
    public void unzipPatchobbToFolder(Context context, String folder, ObbUnzipListener listener) {
    	XAPKFile[] xfs = getPatchXAPKs();
    	unzip(xfs, folder, listener);
    }
    
    private void unzip(XAPKFile[] xfs, String folder, ObbUnzipListener listener) {
    	if (!checkXAPKs(xfs)) {
    		listener.onUnzipFailed();
    	}
    	// run the unzip task
    	else {
        	mListener = listener;
        	new UnzipTask(xAPKS, folder).execute();
    	}
    }
    
    private class UnzipTask extends AsyncTask<Void, Integer, Boolean> {
    	private XAPKFile[] mXFiles;
    	private String mDestFolder;
    	
    	public UnzipTask(XAPKFile[] xfs, String folder) {
    		mXFiles = xfs;
    		mDestFolder = folder;
		}
    	
		@Override
		protected Boolean doInBackground(Void... params) {
			try {
				for (XAPKFile xFile : mXFiles) {
					String fileName = Helpers.getExpansionAPKFileName(mContext, xFile.mIsMain, xFile.mFileVersion);
		            String srcFileName = Helpers.generateSaveFileName(mContext, fileName);
		            File srcFile = new File(srcFileName);
		            if (!unzip(srcFile, mDestFolder)) {
		            	return false;
		            }
				}
				return true;
			} catch (Exception e) {
				return false;
			}
		}
    	
		private boolean unzip(File src, String dstFolder) throws IOException {
			if (!src.exists()) {
                Log.w("APKExpansionUnzip", "Unzip failed, obb file does not exist");
				return false;
			}
			File desDir = new File(dstFolder);
			if (!desDir.exists()) {
				if (!desDir.mkdirs()) {
                    Log.w("APKExpansionUnzip", "Unzip failed, create dirs failed");
                    return false;
                }
			}
			
			mUnzipProgressDlg.setProgress(0);
			ZipFile zf = new ZipFile(src);
			InputStream in = null;
	        OutputStream out = null;
	        try {
				Enumeration<?> entries = zf.entries();
				long totalSize = 0;
				while (entries.hasMoreElements()) {
					ZipEntry entry = ((ZipEntry)entries.nextElement());
					if (entry.isDirectory()) {
						continue;
					}
					totalSize += entry.getSize();
				}
				
				entries = zf.entries();
				long copiedSize = 0;
				int lastPercent = 0;
				while (entries.hasMoreElements()) {
					ZipEntry entry = ((ZipEntry)entries.nextElement());
                    if (entry.isDirectory()) {
                        String str = dstFolder + File.separator + entry.getName();
                        File desFile = new File(str);
                        if (!desFile.exists()) {
                            if (!desFile.mkdirs()) {
                                Log.w("APKExpansionUnzip", "Unzip failed, create dirs failed");
                                return false;
                            }
                        } else {
                            if (!desFile.isDirectory()) {
                                Log.w("APKExpansionUnzip", "Unzip failed, dir conflicts");
                                return false;
                            }
                        }
                    } else {
                        String str = dstFolder + File.separator + entry.getName();
                        File desFile = new File(str);
                        if (!desFile.exists()) {
                            if (!desFile.createNewFile()) {
                                Log.w("APKExpansionUnzip", "Unzip failed, create file failed");
                                return false;
                            }
                        } else {
                            if (desFile.isDirectory()) {
                                Log.w("APKExpansionUnzip", "Unzip failed, file conflicts");
                                return false;
                            }
                        }
                        in = zf.getInputStream(entry);
                        out = new FileOutputStream(desFile);
                        byte buffer[] = new byte[1024];
                        int realLength;
                        while ((realLength = in.read(buffer)) > 0) {
                            out.write(buffer, 0, realLength);
                            copiedSize += realLength;
                            int percent = (int)(copiedSize * 100 / totalSize);
                            if (percent > 100) {
								percent = 100;
							}
                            if (lastPercent != percent) {
                            	lastPercent = percent;
                            	mUnzipProgressDlg.setProgress(percent);
							}
                        }
                    }
				}
			} finally {
				if (in != null) {
					in.close();
				}
				if (out != null) {
					out.close();
				}
				zf.close();
			}
			return true;
	    }
		
		@Override
		protected void onPostExecute(Boolean result) {
			if (result) {
				Log.i("APKExpansionUnzip", "Unzip file success");
				if (mListener != null) {
					mListener.onUnzipComplete();
				}
			} else {
				Log.w("APKExpansionUnzip", "Unzip file failed");
				if (mListener != null) {
					mListener.onUnzipFailed();
				}
			}
			mUnzipProgressDlg.dismiss();
		}
    }
    
    private class UnzipProgressDialog extends ProgressDialog {

		public UnzipProgressDialog(Context context) {
			super(context);
			setIndeterminate(false);
            setCanceledOnTouchOutside(false);
            setCancelable(false);
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            setTitle(ResourceUtil.getString(mContext, "obb_download_title_coping"));
            setProgress(0);
            setMax(100);
		}
	}
}
