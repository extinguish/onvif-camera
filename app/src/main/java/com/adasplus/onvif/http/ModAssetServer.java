/*
 * Copyright (C) 2011-2012 GUIGUI Simon, fyhertz@gmail.com
 *
 * This file is part of Spydroid (http://code.google.com/p/spydroid-ipcamera/)
 *
 * Spydroid is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.adasplus.onvif.http;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.util.Log;

import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.ContentProducer;
import org.apache.http.entity.EntityTemplate;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.cookie.DateParseException;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.util.EntityUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URLDecoder;
import java.util.Date;
import java.util.Locale;

/**
 * Serves the content of assets/www
 * <p>
 * {@link ModAssetServer}本身是被{@link TinyHttpServer}使用.
 * 其中{@link ModAssetServer}是通过反射的方式被{@link TinyHttpServer}
 * 所使用.
 * {@link TinyHttpServer}本身注册了两种服务资源请求处理器，第一个是
 * {@link ModAssetServer},另外一个就是{@link ModInternationalization}.
 */
public class ModAssetServer implements HttpRequestHandler {

    private static final String TAG = "ModAssetServer";

    /**
     * 在{@link TinyHttpServer}当中会获取到{@link #PATTERN},用于进行http请求的
     * 资源匹配.
     * 即在实际当中，谁的匹配度高，就优先调用哪个module的处理策略.
     */
    public static final String PATTERN = "*";

    /**
     * The list of MIME Media Types supported by the server.
     */
    public static String[] mimeMediaTypes = new String[]{
            "htm", "text/html",
            "html", "text/html",
            "gif", "image/gif",
            "jpg", "image/jpeg",
            "png", "image/png",
            "js", "text/javascript",
            "json", "text/json",
            "css", "text/css"
    };

    private final TinyHttpServer mServer;
    private final AssetManager mAssetManager;

    public ModAssetServer(TinyHttpServer server) {
        super();
        mServer = server;
        mAssetManager = mServer.mContext.getAssets();
    }

    @Override
    public void handle(
            final HttpRequest request,
            final HttpResponse response,
            final HttpContext context) throws HttpException, IOException {
        AbstractHttpEntity body;

        final String method = request.getRequestLine().getMethod().toUpperCase(Locale.ENGLISH);
        Log.d(TAG, "Handle the request in ModAssetServer");

        if (!method.equals("GET") && !method.equals("HEAD") && !method.equals("POST")) {
            Log.e(TAG, "the request method \"" + method + "\" are not GET HEAD or POST, we cannot process such request");
            throw new MethodNotSupportedException(method + " method not supported");
        }

        final String url = URLDecoder.decode(request.getRequestLine().getUri());
        if (request instanceof HttpEntityEnclosingRequest) {
            HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
            byte[] entityContent = EntityUtils.toByteArray(entity);
            Log.d(TAG, "Incoming entity content (bytes): " + entityContent.length);
        }

        final String location = "www" + (url.equals("/") ? "/index.htm" : url);
        response.setStatusCode(HttpStatus.SC_OK);

        try {
            Log.i(TAG, "Requested: \"" + url + "\"");

            // Compares the Last-Modified date header (if present) with the If-Modified-Since date
            if (request.containsHeader("If-Modified-Since")) {
                try {
                    Date date = DateUtils.parseDate(request.getHeaders("If-Modified-Since")[0].getValue());
                    if (date.compareTo(mServer.mLastModified) <= 0) {
                        // The file has not been modified
                        response.setStatusCode(HttpStatus.SC_NOT_MODIFIED);
                        return;
                    }
                } catch (DateParseException e) {
                    Log.e(TAG, "DateParse exception happened", e);
                }
            }

            // We determine if the asset is compressed
            try {
                AssetFileDescriptor afd = mAssetManager.openFd(location);

                // The asset is not compressed
                FileInputStream fis = new FileInputStream(afd.getFileDescriptor());
                long skipCount = fis.skip(afd.getStartOffset());
                Log.d(TAG, "the skip count are " + skipCount);
                body = new InputStreamEntity(fis, afd.getDeclaredLength());

                Log.d(TAG, "Serving uncompressed file " + "www" + url);

            } catch (FileNotFoundException e) {
                Log.e(TAG, "fail to find the target request file under asset directory");
                // The asset may be compressed
                // AAPT compresses assets so first we need to uncompress them to determine their length
                InputStream stream = mAssetManager.open(location, AssetManager.ACCESS_STREAMING);
                ByteArrayOutputStream buffer = new ByteArrayOutputStream(64000);
                byte[] tmp = new byte[4096];
                int length = 0;
                while ((length = stream.read(tmp)) != -1) buffer.write(tmp, 0, length);
                body = new InputStreamEntity(new ByteArrayInputStream(buffer.toByteArray()), buffer.size());
                stream.close();

                Log.d(TAG, "Serving compressed file " + "www" + url);
            }

            body.setContentType(getMimeMediaType(url) + "; charset=UTF-8");
            response.addHeader("Last-Modified", DateUtils.formatDate(mServer.mLastModified));
        } catch (IOException e) {
            Log.e(TAG, "response with file not found result to client");
            // File does not exist
            response.setStatusCode(HttpStatus.SC_NOT_FOUND);
            body = new EntityTemplate(new ContentProducer() {
                public void writeTo(final OutputStream outstream) throws IOException {
                    OutputStreamWriter writer = new OutputStreamWriter(outstream, "UTF-8");
                    writer.write("<html><body><h1>");
                    writer.write("File ");
                    writer.write("www" + url);
                    writer.write(" not found");
                    writer.write("</h1></body></html>");
                    writer.flush();
                }
            });
            Log.d(TAG, "File " + "www" + url + " not found");
            body.setContentType("text/html; charset=UTF-8");
        }
        response.setEntity(body);
    }

    private String getMimeMediaType(String fileName) {
        String extension = fileName.substring(fileName.lastIndexOf(".") + 1, fileName.length());
        for (int i = 0; i < mimeMediaTypes.length; i += 2) {
            if (mimeMediaTypes[i].equals(extension))
                return mimeMediaTypes[i + 1];
        }
        return mimeMediaTypes[0];
    }
}
