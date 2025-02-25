/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hudi.table.marker;

import org.apache.hudi.client.common.HoodieSparkEngineContext;
import org.apache.hudi.common.fs.FSUtils;
import org.apache.hudi.common.model.IOType;
import org.apache.hudi.common.testutils.FileSystemTestUtils;
import org.apache.hudi.common.testutils.HoodieCommonTestHarness;
import org.apache.hudi.common.util.CollectionUtils;
import org.apache.hudi.exception.HoodieException;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.spark.api.java.JavaSparkContext;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class TestWriteMarkersBase extends HoodieCommonTestHarness {

  protected WriteMarkers writeMarkers;
  protected FileSystem fs;
  protected Path markerFolderPath;
  protected JavaSparkContext jsc;
  protected HoodieSparkEngineContext context;

  private void createSomeMarkers() {
    writeMarkers.create("2020/06/01", "file1", IOType.MERGE);
    writeMarkers.create("2020/06/02", "file2", IOType.APPEND);
    writeMarkers.create("2020/06/03", "file3", IOType.CREATE);
  }

  private void createInvalidFile(String partitionPath, String invalidFileName) {
    Path path = FSUtils.getPartitionPath(markerFolderPath.toString(), partitionPath);
    Path invalidFilePath = new Path(path, invalidFileName);
    try {
      fs.create(invalidFilePath, false).close();
    } catch (IOException e) {
      throw new HoodieException("Failed to create invalid file " + invalidFilePath, e);
    }
  }

  abstract void verifyMarkersInFileSystem() throws IOException;

  @Test
  public void testCreation() throws Exception {
    // when
    createSomeMarkers();

    // then
    assertTrue(fs.exists(markerFolderPath));
    verifyMarkersInFileSystem();
  }

  @Test
  public void testDeletionWhenMarkerDirExists() throws IOException {
    //when
    writeMarkers.create("2020/06/01", "file1", IOType.MERGE);

    // then
    assertTrue(writeMarkers.doesMarkerDirExist());
    assertTrue(writeMarkers.deleteMarkerDir(context, 2));
    assertFalse(writeMarkers.doesMarkerDirExist());
  }

  @Test
  public void testDeletionWhenMarkerDirNotExists() throws IOException {
    // then
    assertFalse(writeMarkers.doesMarkerDirExist());
    assertTrue(writeMarkers.allMarkerFilePaths().isEmpty());
    assertFalse(writeMarkers.deleteMarkerDir(context, 2));
  }

  @Test
  public void testDataPathsWhenCreatingOrMerging() throws IOException {
    // add markfiles
    createSomeMarkers();
    // add invalid file
    createInvalidFile("2020/06/01", "invalid_file3");
    int fileSize = FileSystemTestUtils.listRecursive(fs, markerFolderPath).size();
    assertEquals(fileSize,4);

    // then
    assertIterableEquals(CollectionUtils.createImmutableList(
        "2020/06/01/file1", "2020/06/03/file3"),
        writeMarkers.createdAndMergedDataPaths(context, 2).stream().sorted().collect(Collectors.toList())
    );
  }

  @Test
  public void testAllMarkerPaths() throws IOException {
    // given
    createSomeMarkers();

    // then
    assertIterableEquals(CollectionUtils.createImmutableList("2020/06/01/file1.marker.MERGE",
        "2020/06/02/file2.marker.APPEND", "2020/06/03/file3.marker.CREATE"),
        writeMarkers.allMarkerFilePaths().stream().sorted().collect(Collectors.toList())
    );
  }

  @Test
  public void testStripMarkerSuffix() {
    // Given
    final String pathPrefix = "file://" + metaClient.getMetaPath() + "/file";
    final String markerFilePath = pathPrefix + ".marker.APPEND";

    // when-then
    assertEquals(pathPrefix, WriteMarkers.stripMarkerSuffix(markerFilePath));
  }
}
