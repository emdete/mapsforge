/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014 Ludwig M Brinckmann
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.mapsforge.map.layer.download;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.zip.GZIPInputStream;

import org.mapsforge.core.graphics.CorruptedInputStreamException;
import org.mapsforge.core.graphics.GraphicFactory;
import org.mapsforge.core.graphics.TileBitmap;
import org.mapsforge.core.util.IOUtils;

class TileDownloader {
	private static int TIMEOUT_CONNECT = 5000;
	private static int TIMEOUT_READ = 10000;
	private static String user_agent = null;
	private static String referer = null;
	private static boolean follow_redirects = false;

	public static int getTimeoutConnect() {
		return TIMEOUT_CONNECT;
	}

	public static void setTimeoutConnect(int timeoutConnect) {
		TIMEOUT_CONNECT = timeoutConnect;
	}

	public static int getTimeoutRead() {
		return TIMEOUT_READ;
	}

	public static void setTimeoutRead(int timeoutRead) {
		TIMEOUT_READ = timeoutRead;
	}

	public static String getUser_agent() {
		return user_agent;
	}

	public static void setUser_agent(String user_agent) {
		TileDownloader.user_agent = user_agent;
	}

	public static String getReferer() {
		return referer;
	}

	public static void setReferer(String referer) {
		TileDownloader.referer = referer;
	}

	public static boolean isFollow_redirects() {
		return follow_redirects;
	}

	public static void setFollow_redirects(boolean follow_redirects) {
		TileDownloader.follow_redirects = follow_redirects;
	}

	private static InputStream getInputStream(URLConnection urlConnection) throws IOException {
		if ("gzip".equals(urlConnection.getContentEncoding())) {
			return new GZIPInputStream(urlConnection.getInputStream());
		}
		return urlConnection.getInputStream();
	}

	private static URLConnection getURLConnection(URL url) throws IOException {
		URLConnection urlConnection = url.openConnection();
		urlConnection.setConnectTimeout(TIMEOUT_CONNECT);
		urlConnection.setReadTimeout(TIMEOUT_READ);
		if (user_agent != null) {
			urlConnection.setRequestProperty("User-Agent", user_agent);
		}
		if (referer != null) {
			urlConnection.setRequestProperty("Referer", referer);
		}
		if (urlConnection instanceof HttpURLConnection) {
			((HttpURLConnection)urlConnection).setInstanceFollowRedirects(follow_redirects);
		}
		return urlConnection;
	}

	private final DownloadJob downloadJob;
	private final GraphicFactory graphicFactory;

	TileDownloader(DownloadJob downloadJob, GraphicFactory graphicFactory) {
		if (downloadJob == null) {
			throw new IllegalArgumentException("downloadJob must not be null");
		} else if (graphicFactory == null) {
			throw new IllegalArgumentException("graphicFactory must not be null");
		}

		this.downloadJob = downloadJob;
		this.graphicFactory = graphicFactory;
	}

	TileBitmap downloadImage() throws IOException {
		URL url = this.downloadJob.tileSource.getTileUrl(this.downloadJob.tile);
		URLConnection urlConnection = getURLConnection(url);
		if (urlConnection instanceof HttpURLConnection
				&& ((HttpURLConnection)urlConnection).getResponseCode() != HttpURLConnection.HTTP_OK) {
			throw new IOException("http response code=" + ((HttpURLConnection)urlConnection).getResponseCode());
		}
		InputStream inputStream = getInputStream(urlConnection);

		try {
			TileBitmap result = this.graphicFactory.createTileBitmap(inputStream, this.downloadJob.tile.tileSize,
					this.downloadJob.hasAlpha);
			result.setExpiration(urlConnection.getExpiration());
			return result;
		} catch (CorruptedInputStreamException e) {
			// the creation of the tile bit map can fail at, at least on Android,
			// when the connection is slow or busy, returning null here ensures that
			// the tile will be downloaded again
			return null;
		} finally {
			IOUtils.closeQuietly(inputStream);
		}
	}
}
