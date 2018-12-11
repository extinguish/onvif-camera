/*
 * Copyright (C) 2011-2013 GUIGUI Simon, fyhertz@gmail.com
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

import android.util.Log;

import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentProducer;
import org.apache.http.entity.EntityTemplate;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;

/**
 * HTTP server of Spydroid.
 * <p>
 * Its document root is assets/www, it contains a little user-friendly website to control spydroid from a browser.
 * <p>
 * Some commands can be sent to it by sending POST request to "/request.json".
 * See {@link RequestHandler} to find out what kind of commands can be sent.
 * <p>
 * Streams can also be started/stopped by sending GET request to "/spydroid.sdp".
 * The HTTP server then responds with a proper Session Description (SDP).
 * All supported options are described in UriParser
 */
public class CustomHttpServer extends TinyHttpServer {
    private static final String TAG = "customHttpServer";

    /**
     * A stream failed to start.
     */
//    public final static int ERROR_START_FAILED = 0xFE;

    /**
     * Streaming started.
     */
//    public final static int MESSAGE_STREAMING_STARTED = 0X00;

    /**
     * Streaming stopped.
     */
//    public final static int MESSAGE_STREAMING_STOPPED = 0X01;

    /**
     * Maximal number of streams that you can start from the HTTP server.
     **/
    protected static final int MAX_STREAM_NUM = 2;

    public CustomHttpServer() {

        // The common name that appears in the CA of the HTTPS server of Spydroid
        mCACommonName = "Spydroid CA";

        // If at some point a stream cannot start the exception is stored so that
        // it can be fetched in the HTTP interface to display an appropriate message
        CallbackListener listener = new CallbackListener() {
            @Override
            public void onError(TinyHttpServer server, Exception e, int error) {
//                if (error == ERROR_START_FAILED) {
                    // SpydroidApplication.getInstance().lastCaughtException = e;
//                }
            }

            @Override
            public void onMessage(TinyHttpServer server, int message) {
            }
        };
        addCallbackListener(listener);

        // HTTP is used by default for now
        mHttpEnabled = true;
        mHttpsEnabled = false;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        addRequestHandler("/request.json*", new CustomRequestHandler());
    }

    @Override
    public void stop() {
        super.stop();
    }

    class CustomRequestHandler implements HttpRequestHandler {

        public CustomRequestHandler() {
        }

        public void handle(HttpRequest request, HttpResponse response, HttpContext arg2) throws HttpException, IOException {
            if (request.getRequestLine().getMethod().equals("POST")) {
                // Retrieve the POST content
                HttpEntityEnclosingRequest post = (HttpEntityEnclosingRequest) request;
                byte[] entityContent = EntityUtils.toByteArray(post.getEntity());
                String content = new String(entityContent, Charset.forName("UTF-8"));
                Log.d(TAG, "handle request of " + content);

                // Execute the request
                final String json = RequestHandler.handle(content);

                // Return the response
                EntityTemplate body = new EntityTemplate(new ContentProducer() {
                    public void writeTo(final OutputStream outstream) throws IOException {
                        OutputStreamWriter writer = new OutputStreamWriter(outstream, "UTF-8");
                        writer.write(json);
                        writer.flush();
                    }
                });
                response.setStatusCode(HttpStatus.SC_OK);
                body.setContentType("application/json; charset=UTF-8");
                response.setEntity(body);
            }
        }
    }
}
