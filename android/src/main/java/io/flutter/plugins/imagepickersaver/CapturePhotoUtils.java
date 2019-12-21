package io.flutter.plugins.imagepickersaver;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLConnection;

/**
 * Android internals have been modified to store images in the media folder with
 * the correct date meta data
 *
 * @author samuelkirton
 */
public class CapturePhotoUtils {

    /**
     * A copy of the Android internals  insertImage method, this method populates the
     * meta data with DATE_ADDED and DATE_TAKEN. This fixes a common problem where media
     * that is inserted manually gets saved at the end of the gallery (because date is not populated).
     *
     * @see android.provider.MediaStore.Images.Media#insertImage(ContentResolver, Bitmap, String, String)
     */

    public static final String insertImage(ContentResolver cr,
                                           byte[] source,
                                           String title,
                                           String description, Context context) throws IOException {

        Bitmap bmp =  BitmapFactory.decodeByteArray(source, 0, source.length);

        //系统相册目录
        String galleryPath = Environment.getExternalStorageDirectory()
                + File.separator + Environment.DIRECTORY_DCIM
                + File.separator + "Camera" + File.separator;
        String photoName = "";
        if(!title.endsWith("Camera")){
            // 由flutter生成hash可以保证hash值一致性,
            photoName=title + ".jpg";
        }else{
            photoName=System.currentTimeMillis() + ".jpg";
        }
//        Log.i("保存图片", "图片名称"+photoName+"title"+title,new IllegalArgumentException("参数错误"));
        File file = null;
        String fileName = "";
        // 声明输出流
        FileOutputStream outStream = null;
        try {
            file = new File(galleryPath, photoName);
            fileName  = file.toString();
            outStream = new FileOutputStream(fileName);
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, outStream);
        }catch (Exception e) {
            e.getStackTrace();
        } finally {
            try {
                if (outStream != null) {
                    outStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
//            MediaStore.Images.Media.insertImage(context.getContentResolver(),bmp,fileName,null);
            Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            Uri uri = Uri.fromFile(file);
            intent.setData(uri);
            context.sendBroadcast(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return galleryPath;
    }


//    public static final String insertImage(ContentResolver cr,
//                                           byte[] source,
//                                           String title,
//                                           String description) throws IOException {
//
//        InputStream is = new BufferedInputStream(new ByteArrayInputStream(source));
//        String mimeType = URLConnection.guessContentTypeFromStream(is);
//
//        ContentValues values = new ContentValues();
//        values.put(Images.Media.TITLE, title);
//        values.put(Images.Media.DISPLAY_NAME, title);
//        values.put(Images.Media.DESCRIPTION, description);
//        values.put(Images.Media.MIME_TYPE, mimeType);
//        // Add the date meta data to ensure the image is added at the front of the gallery
//        values.put(Images.Media.DATE_ADDED, System.currentTimeMillis());
//        values.put(Images.Media.DATE_TAKEN, System.currentTimeMillis());
//
//        Uri url = null;
//        String stringUrl = "";    /* value to be returned */
//
//        try {
//            String galleryPath = Environment.getExternalStorageDirectory()
//                    + File.separator + Environment.DIRECTORY_DCIM
//                    + File.separator + "Camera" + File.separator;
//            url = cr.insert(Images.Media.EXTERNAL_CONTENT_URI, values);
//
//            if (source != null) {
//                OutputStream imageOut = cr.openOutputStream(url);
//                try {
//                    //source.compress(Bitmap.CompressFormat.JPEG, 100, imageOut);
//                    imageOut.write(source);
//                } finally {
//                    imageOut.close();
//                }
//
//                long id = ContentUris.parseId(url);
//                // Wait until MINI_KIND thumbnail is generated.
//                Bitmap miniThumb = Images.Thumbnails.getThumbnail(cr, id, Images.Thumbnails.MINI_KIND, null);
//                // This is for backward compatibility.
//                storeThumbnail(cr, miniThumb, id, 50F, 50F, Images.Thumbnails.MICRO_KIND);
//
//
//            } else {
//                cr.delete(url, null, null);
//                url = null;
//            }
//        } catch (Exception e) {
//            if (url != null) {
//                cr.delete(url, null, null);
//                url = null;
//            }
//        }
//
//        if (url != null) {
//            stringUrl = getFilePathFromContentUri(url, cr);
//        }
//
//
//        return stringUrl;
//    }

    /**
     * A copy of the Android internals StoreThumbnail method, it used with the insertImage to
     * populate the android.provider.MediaStore.Images.Media#insertImage with all the correct
     * meta data. The StoreThumbnail method is private so it must be duplicated here.
     *
     * @see android.provider.MediaStore.Images.Media (StoreThumbnail private method)
     */
    private static final Bitmap storeThumbnail(
            ContentResolver cr,
            Bitmap source,
            long id,
            float width,
            float height,
            int kind) {

        // create the matrix to scale it
        Matrix matrix = new Matrix();

        float scaleX = width / source.getWidth();
        float scaleY = height / source.getHeight();

        matrix.setScale(scaleX, scaleY);

        Bitmap thumb = Bitmap.createBitmap(source, 0, 0,
                source.getWidth(),
                source.getHeight(), matrix,
                true
        );

        ContentValues values = new ContentValues(4);
        values.put(Images.Thumbnails.KIND, kind);
        values.put(Images.Thumbnails.IMAGE_ID, (int) id);
        values.put(Images.Thumbnails.HEIGHT, thumb.getHeight());
        values.put(Images.Thumbnails.WIDTH, thumb.getWidth());

        Uri url = cr.insert(Images.Thumbnails.EXTERNAL_CONTENT_URI, values);

        try {
            OutputStream thumbOut = cr.openOutputStream(url);
            //thumb.compress(Bitmap.CompressFormat.JPEG, 100, thumbOut);
            thumbOut.close();
            return thumb;
        } catch (FileNotFoundException ex) {
            return null;
        } catch (IOException ex) {
            return null;
        }
    }

    /**
     * Gets the corresponding path to a file from the given content:// URI
     *
     * @param selectedVideoUri The content:// URI to find the file path from
     * @param contentResolver  The content resolver to use to perform the query.
     * @return the file path as a string
     */
    public static String getFilePathFromContentUri(Uri selectedVideoUri,
                                                   ContentResolver contentResolver) {
        String filePath;
        String[] filePathColumn = {MediaStore.MediaColumns.DATA};

        Cursor cursor = contentResolver.query(selectedVideoUri, filePathColumn, null, null, null);
//	    也可用下面的方法拿到cursor
//	    Cursor cursor = this.context.managedQuery(selectedVideoUri, filePathColumn, null, null, null);

        cursor.moveToFirst();

        int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
        filePath = cursor.getString(columnIndex);
        cursor.close();
        return filePath;
    }
}
