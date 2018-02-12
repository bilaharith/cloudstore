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

import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.shell.CommandFormat;
import org.apache.hadoop.fs.store.DurationInfo;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.util.ExitUtil;
import org.apache.hadoop.util.ToolRunner;

import static org.apache.hadoop.util.VersionInfo.getDate;
import static org.apache.hadoop.util.VersionInfo.getProtocVersion;
import static org.apache.hadoop.util.VersionInfo.getSrcChecksum;
import static org.apache.hadoop.util.VersionInfo.getUser;
import static org.apache.hadoop.util.VersionInfo.getVersion;

public class StoreDiag extends StoreEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(StoreDiag.class);

  private static final String HELLO = "Hello";


  CommandFormat commandFormat = new CommandFormat(0, Integer.MAX_VALUE);


  // Exit codes
  static final int SUCCESS = 0;

  static final int E_USAGE = 42;

  static final int ERROR = -1;

  static final String USAGE = "Usage: StoreDiag <filesystem>";

  private void printJVMOptions() {
    heading("System Properties");
    Properties sysProps = System.getProperties();
    TreeSet<String> sorted = new TreeSet<>();
    for (Object k : sysProps.keySet()) {
      sorted.add(k.toString());
    }
    for (String s : sorted) {
      println("%s = \"%s\"", s, sysProps.getProperty(s));
    }
  }

  private void printOptions(Configuration conf, Object[][] options) {
    if (options.length > 0) {
      heading("Selected and Sanitized Configuration Options");
      for (int i = 0; i < options.length; i++) {
        printOption(conf, (String) options[i][0], (Boolean) options[i][1]);
      }
    }
  }

  private void printOption(Configuration conf, String key, boolean sensitive) {
    String v = conf.get(key);
    if (v == null) {
      v = "(unset)";
    } else {
      if (sensitive) {
        int len = v.length();
        if (len > 2) {
          StringBuilder b = new StringBuilder(len);
          b.append(v.charAt(0));
          for (int i = 1; i < len - 1; i++) {
            b.append('*');
          }
          b.append(v.charAt(len - 1));
          v = b.toString();
        } else {
          // short values get special treatment
          v = "**";
        }
      }
    }
    println("%s = %s", key, v);
  }

  @Override
  public final int run(String[] args) throws Exception {
    return run(args, System.out);
  }

  public int run(String[] args, PrintStream stream) throws Exception {
    setOut(stream);
    List<String> paths = parseArgs(args);
    if (paths.size() != 1) {
      errorln(USAGE);
      return E_USAGE;
    }
    heading("Hadoop information");
    println("  Hadoop %s", getVersion());
    println("  Compiled by %s on %s", getUser(), getDate());
    println("  Compiled with protoc %s", getProtocVersion());
    println("  From source with checksum %s", getSrcChecksum());


    Configuration conf = getConf();
    Path path = new Path(paths.get(0));

    URI fsURI = path.toUri();

    heading("Diagnostics for filesystem %s", fsURI);

    StoreDiagnosticsInfo store;
    switch (fsURI.getScheme()) {
    case "s3a":
      store = new S3ADiagnosticsInfo(fsURI);
      break;
    default:
      store = new StoreDiagnosticsInfo(fsURI);
    }
    println("%s\n%s\n%s",
        store.getName(), store.getDescription(), store.getHomepage());

    printJVMOptions();

    conf = store.patchConfigurationToInitalization(conf);

    printOptions(conf, store.getFilesystemOptions());

    heading("Endpoints");
    for (URI endpoint : store.listEndpointsToProbe(conf)) {
      probeOneEndpoint(endpoint);
    }


    executeFileSystemOperations(conf, path);

    // Validate parameters.
    return SUCCESS;
  }


  private void probeOneEndpoint(URI endpoint) throws IOException {
    final String host = endpoint.getHost();
    InetAddress addr = InetAddress.getByName(host);
    println("%s (%s) has IP address %s",
        endpoint,
        addr.getCanonicalHostName(),
        addr.getHostAddress());
  }

  /**
   * Execute the FS level operations, one by one.
   * @param conf
   * @param path
   * @throws IOException
   */
  private void executeFileSystemOperations(final Configuration conf,
      final Path path) throws IOException {
    FileSystem fs = path.getFileSystem(conf);

    heading("Test filesystem %s", path);
    println("%s", fs);


    Path root = fs.makeQualified(new Path("/"));
    try (DurationInfo d = new DurationInfo(LOG,
        "Listing  %s", root)) {
      println("%s root entry count: %d", root, fs.listStatus(root).length);
    }

    String dirName = "dir-" + UUID.randomUUID();
    Path dir = new Path(root, dirName);
    try (DurationInfo d = new DurationInfo(LOG,
        "Creating a directory %s", dir)) {
      fs.mkdirs(dir);
    }
    try {
      Path file = new Path(dir, "file");
      try (DurationInfo d = new DurationInfo(LOG,
          "Creating a file %s", file)) {
        FSDataOutputStream data = fs.create(file, true);
        data.writeUTF(HELLO);
        data.close();
      }
      try (DurationInfo d = new DurationInfo(LOG,
          "Listing  %s", dir)) {
        fs.listFiles(dir, false);
      }

      try (DurationInfo d = new DurationInfo(LOG,
          "Reading a file %s", file)) {
        FSDataInputStream in = fs.open(file);
        String utf = in.readUTF();
        in.close();
        if (!HELLO.equals(utf)) {
          throw new IOException("Expected " + file + " to contain the text "
              + HELLO + " -but it has the text \"" + utf + "\"");
        }
      }
      try (DurationInfo d = new DurationInfo(LOG,
          "Deleting file %s", file)) {
        fs.delete(file, true);
      }
    } finally {
      try (DurationInfo d = new DurationInfo(LOG,
          "Deleting directory %s", dir)) {
        try {
          fs.delete(dir, true);
        } catch (Exception e) {
          LOG.warn("When deleting {}: ", dir, e);
        }
      }
    }
  }


  /**
   * Parse CLI arguments and returns the position arguments.
   * The options are stored in {@link #commandFormat}.
   *
   * @param args command line arguments.
   * @return the position arguments from CLI.
   */
  List<String> parseArgs(String[] args) {
    return args.length > 0 ? commandFormat.parse(args, 0)
        : new ArrayList<String>(0);
  }

  /**
   * Execute the command, return the result or throw an exception,
   * as appropriate.
   * @param args argument varags.
   * @return return code
   * @throws Exception failure
   */
  public static int exec(String... args) throws Exception {
    return ToolRunner.run(new StoreDiag(), args);
  }

  /**
   * Main entry point. Calls {@code System.exit()} on all execution paths.
   * @param args argument list
   */
  public static void main(String[] args) {
    try {

      exit(exec(args), "");
    } catch (CommandFormat.UnknownOptionException e) {
      errorln(e.getMessage());
      exit(E_USAGE, e.getMessage());
    } catch (Throwable e) {
      e.printStackTrace(System.err);
      exit(ERROR, e.toString());
    }
  }

  protected static void exit(int status, String text) {
    ExitUtil.terminate(status, text);
  }
}