/*
 * Copyright (c) 2018, Loong Wan (https://github.com/loong10k).
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package okhttp3.extension.ssl;

import javax.net.ssl.SSLSession;

/**
 * {@link OkHttpHostnameVerifier} that unconditionally returns {@code true}, disabling all
 * hostname verification.
 *
 * <p><b>Security warning:</b> this verifier makes HTTPS connections vulnerable to
 * man-in-the-middle attacks and must never be used in production. It is provided only for
 * local development against self-signed certificates.</p>
 *
 * @deprecated use the JVM default {@code OkHostnameVerifier}. This class is scheduled for
 *             removal in a future major release.
 */
@Deprecated
public class TrustAllHostnameVerifier implements OkHttpHostnameVerifier {

	@Override
	public boolean verify(String hostname, SSLSession session) {
		return true;
	}

}
