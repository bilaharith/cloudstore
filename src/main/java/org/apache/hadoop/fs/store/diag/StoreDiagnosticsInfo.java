/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.fs.store.diag;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.conf.Configuration;

class StoreDiagnosticsInfo {

  protected static final Object[][] EMPTY_OPTIONS = {};

  protected static final String[] EMPTY_CLASSNAMES = {};

  protected static final List<URI> EMPTY_ENDPOINTS = new ArrayList<>(0);

  protected final URI fsURI;

  public StoreDiagnosticsInfo(final URI fsURI) {
    this.fsURI = fsURI;
  }

  /**
   * Get the filesystem name.
   * @return FS name
   */
  public String getName() {
    return "Store for scheme " + fsURI.getScheme();
  }

  /**
   * Any extra description.
   * @return
   */
  public String getDescription() {
    return "";
  }

  /**
   * Any home page of the filesystem.
   * @return a string to turn into a URL if not empty.
   */
  public String getHomepage() {
    return "";
  }

  /**
   * List of options for filesystems. Each entry must be a pair of
   * (string, sensitive); sensitive strings don't have their values
   * fully printed.
   * @return
   */
  public Object[][] getFilesystemOptions() {
      return EMPTY_OPTIONS;
  }

  /**
   * Take the raw config and patch as the FS will have during
   * initialization.
   * This handles stores like S3A which do some per-bucket config.
   * @param conf initial configuration.
   * @return the configuration to work with.
   */
  public Configuration patchConfigurationToInitalization(final Configuration conf) {
    return conf;
  }

  public String[] getClassnames() {
    return EMPTY_CLASSNAMES;
  }

  /**
   * List the endpoints to probe for (auth, REST, etc).
   * @param conf configuration to use, will already have been patched.
   * @return a possibly empty ist of endpoints for DNS lookup then HTTP connect to.
   */
  public List<URI> listEndpointsToProbe(Configuration conf)
      throws URISyntaxException {
    return EMPTY_ENDPOINTS;
  }
}
