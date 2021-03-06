/*
 * Copyright 2012-2017 CodeLibs Project and the Others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.codelibs.fess.app.web.admin.esreq;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Locale;

import org.codelibs.core.io.CopyUtil;
import org.codelibs.core.io.ReaderUtil;
import org.codelibs.core.lang.StringUtil;
import org.codelibs.elasticsearch.runner.net.Curl;
import org.codelibs.elasticsearch.runner.net.CurlRequest;
import org.codelibs.elasticsearch.runner.net.CurlResponse;
import org.codelibs.fess.Constants;
import org.codelibs.fess.app.web.base.FessAdminAction;
import org.codelibs.fess.util.ResourceUtil;
import org.lastaflute.web.Execute;
import org.lastaflute.web.response.ActionResponse;
import org.lastaflute.web.response.HtmlResponse;
import org.lastaflute.web.ruts.process.ActionRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author shinsuke
 */
public class AdminEsreqAction extends FessAdminAction {

    private static final Logger logger = LoggerFactory.getLogger(AdminEsreqAction.class);

    @Override
    protected void setupHtmlData(final ActionRuntime runtime) {
        super.setupHtmlData(runtime);
        runtime.registerData("helpLink", systemHelper.getHelpLink(fessConfig.getOnlineHelpNameEsreq()));
    }

    @Execute
    public HtmlResponse index() {
        return asListHtml(() -> saveToken());
    }

    @Execute
    public ActionResponse upload(final UploadForm form) {
        validate(form, messages -> {}, () -> asListHtml(null));
        verifyToken(() -> asListHtml(() -> saveToken()));

        String header = null;
        final StringBuilder buf = new StringBuilder(1000);
        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(form.requestFile.getInputStream(), Constants.UTF_8))) {
            header = ReaderUtil.readLine(reader);
            String line;
            while ((line = ReaderUtil.readLine(reader)) != null) {
                buf.append(line);
            }
        } catch (final Exception e) {
            throwValidationError(messages -> messages.addErrorsFailedToReadRequestFile(GLOBAL, e.getMessage()), () -> {
                return asListHtml(() -> saveToken());
            });
        }

        final CurlRequest curlRequest = getCurlRequest(header);
        if (curlRequest == null) {
            final String msg = header;
            throwValidationError(messages -> messages.addErrorsInvalidHeaderForRequestFile(GLOBAL, msg), () -> {
                return asListHtml(() -> saveToken());
            });
        } else {
            try (final CurlResponse response = curlRequest.body(buf.toString()).execute()) {
                final File tempFile = File.createTempFile("esreq_", ".json");
                try (final InputStream in = response.getContentAsStream()) {
                    CopyUtil.copy(in, tempFile);
                } catch (final Exception e1) {
                    if (tempFile != null && tempFile.exists() && !tempFile.delete()) {
                        logger.warn("Failed to delete " + tempFile.getAbsolutePath());
                    }
                    throw e1;
                }
                return asStream("es_" + System.currentTimeMillis() + ".json").contentTypeOctetStream().stream(out -> {
                    try (final InputStream in = new FileInputStream(tempFile)) {
                        out.write(in);
                    } finally {
                        if (tempFile.exists() && !tempFile.delete()) {
                            logger.warn("Failed to delete " + tempFile.getAbsolutePath());
                        }
                    }
                });
            } catch (final Exception e) {
                logger.warn("Failed to process request file: " + form.requestFile.getFileName(), e);
                throwValidationError(messages -> messages.addErrorsInvalidHeaderForRequestFile(GLOBAL, e.getMessage()), () -> {
                    return asListHtml(() -> saveToken());
                });
            }
        }
        return redirect(getClass()); // no-op
    }

    private CurlRequest getCurlRequest(final String header) {
        if (StringUtil.isBlank(header)) {
            return null;
        }

        final String[] values = header.split(" ");
        if (values.length != 2) {
            return null;
        }

        final String url;
        if (values[1].startsWith("/")) {
            url = ResourceUtil.getElasticsearchHttpUrl() + values[1];
        } else {
            url = ResourceUtil.getElasticsearchHttpUrl() + "/" + values[1];
        }

        switch (values[0].toUpperCase(Locale.ROOT)) {
        case "GET":
            return Curl.get(url);
        case "POST":
            return Curl.post(url);
        case "PUT":
            return Curl.put(url);
        case "DELETE":
            return Curl.delete(url);
        default:
            break;
        }
        return null;
    }

    private HtmlResponse asListHtml(final Runnable runnable) {
        if (runnable != null) {
            runnable.run();
        }
        return asHtml(path_AdminEsreq_AdminEsreqJsp).useForm(UploadForm.class);
    }

}
