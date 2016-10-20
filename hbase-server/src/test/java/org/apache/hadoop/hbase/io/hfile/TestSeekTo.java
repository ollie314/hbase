/**
 *
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
package org.apache.hadoop.hbase.io.hfile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.Tag;
import org.apache.hadoop.hbase.io.encoding.DataBlockEncoding;
import org.apache.hadoop.hbase.testclassification.SmallTests;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Test {@link HFileScanner#seekTo(byte[])} and its variants.
 */
@Category(SmallTests.class)
@RunWith(Parameterized.class)
public class TestSeekTo {

  private final static HBaseTestingUtility TEST_UTIL = new HBaseTestingUtility();
  private final DataBlockEncoding encoding;

  @Parameters
  public static Collection<Object[]> parameters() {
    List<Object[]> paramList = new ArrayList<Object[]>();
    for (DataBlockEncoding encoding : DataBlockEncoding.values()) {
      paramList.add(new Object[] { encoding });
    }
    return paramList;
  }

  static boolean switchKVs = false;

  public TestSeekTo(DataBlockEncoding encoding) {
    this.encoding = encoding;
  }

  @Before
  public void setUp() {
    // reset
    switchKVs = false;
     }


  static KeyValue toKV(String row, TagUsage tagUsage) {
    if (tagUsage == TagUsage.NO_TAG) {
      return new KeyValue(Bytes.toBytes(row), Bytes.toBytes("family"), Bytes.toBytes("qualifier"),
          Bytes.toBytes("value"));
    } else if (tagUsage == TagUsage.ONLY_TAG) {
      Tag t = new Tag((byte) 1, "myTag1");
      Tag[] tags = new Tag[1];
      tags[0] = t;
      return new KeyValue(Bytes.toBytes(row), Bytes.toBytes("family"), Bytes.toBytes("qualifier"),
          HConstants.LATEST_TIMESTAMP, Bytes.toBytes("value"), tags);
    } else {
      if (!switchKVs) {
        switchKVs = true;
        return new KeyValue(Bytes.toBytes(row), Bytes.toBytes("family"),
            Bytes.toBytes("qualifier"), HConstants.LATEST_TIMESTAMP, Bytes.toBytes("value"));
      } else {
        switchKVs = false;
        Tag t = new Tag((byte) 1, "myTag1");
        Tag[] tags = new Tag[1];
        tags[0] = t;
        return new KeyValue(Bytes.toBytes(row), Bytes.toBytes("family"),
            Bytes.toBytes("qualifier"), HConstants.LATEST_TIMESTAMP, Bytes.toBytes("value"), tags);
      }
    }
  }
  static String toRowStr(KeyValue kv) {
    return Bytes.toString(kv.getRow());
  }

  Path makeNewFile(TagUsage tagUsage) throws IOException {
    Path ncTFile = new Path(TEST_UTIL.getDataTestDir(), "basic.hfile");
    FSDataOutputStream fout = TEST_UTIL.getTestFileSystem().create(ncTFile);
    Configuration conf = TEST_UTIL.getConfiguration();
    if (tagUsage != TagUsage.NO_TAG) {
      conf.setInt("hfile.format.version", 3);
    } else {
      conf.setInt("hfile.format.version", 2);
    }
    int blocksize = toKV("a", tagUsage).getLength() * 3;
    HFileContext context = new HFileContextBuilder().withBlockSize(blocksize)
        .withDataBlockEncoding(encoding)
        .withIncludesTags(true).build();
    HFile.Writer writer = HFile.getWriterFactoryNoCache(conf).withOutputStream(fout)
          .withFileContext(context)
          // NOTE: This test is dependent on this deprecated nonstandard
          // comparator
          .withComparator(KeyValue.COMPARATOR).create();
    // 4 bytes * 3 * 2 for each key/value +
    // 3 for keys, 15 for values = 42 (woot)
    writer.append(toKV("c", tagUsage));
    writer.append(toKV("e", tagUsage));
    writer.append(toKV("g", tagUsage));
    // block transition
    writer.append(toKV("i", tagUsage));
    writer.append(toKV("k", tagUsage));
    writer.close();
    fout.close();
    return ncTFile;
  }

  @Test
  public void testSeekBefore() throws Exception {
    testSeekBeforeInternals(TagUsage.NO_TAG);
    testSeekBeforeInternals(TagUsage.ONLY_TAG);
    testSeekBeforeInternals(TagUsage.PARTIAL_TAG);
  }

  protected void testSeekBeforeInternals(TagUsage tagUsage) throws IOException {
    Path p = makeNewFile(tagUsage);
    FileSystem fs = TEST_UTIL.getTestFileSystem();
    Configuration conf = TEST_UTIL.getConfiguration();
    HFile.Reader reader = HFile.createReader(fs, p, new CacheConfig(conf), conf);
    reader.loadFileInfo();
    HFileScanner scanner = reader.getScanner(false, true);
    assertFalse(scanner.seekBefore(toKV("a", tagUsage).getKey()));

    assertFalse(scanner.seekBefore(toKV("c", tagUsage).getKey()));

    assertTrue(scanner.seekBefore(toKV("d", tagUsage).getKey()));
    assertEquals("c", toRowStr(scanner.getKeyValue()));

    assertTrue(scanner.seekBefore(toKV("e", tagUsage).getKey()));
    assertEquals("c", toRowStr(scanner.getKeyValue()));

    assertTrue(scanner.seekBefore(toKV("f", tagUsage).getKey()));
    assertEquals("e", toRowStr(scanner.getKeyValue()));

    assertTrue(scanner.seekBefore(toKV("g", tagUsage).getKey()));
    assertEquals("e", toRowStr(scanner.getKeyValue()));
    assertTrue(scanner.seekBefore(toKV("h", tagUsage).getKey()));
    assertEquals("g", toRowStr(scanner.getKeyValue()));
    assertTrue(scanner.seekBefore(toKV("i", tagUsage).getKey()));
    assertEquals("g", toRowStr(scanner.getKeyValue()));
    assertTrue(scanner.seekBefore(toKV("j", tagUsage).getKey()));
    assertEquals("i", toRowStr(scanner.getKeyValue()));
    Cell cell = scanner.getKeyValue();
    if (tagUsage != TagUsage.NO_TAG && cell.getTagsLength() > 0) {
      Iterator<Tag> tagsIterator = CellUtil.tagsIterator(cell.getTagsArray(), cell.getTagsOffset(),
          cell.getTagsLength());
      while (tagsIterator.hasNext()) {
        Tag next = tagsIterator.next();
        assertEquals("myTag1", Bytes.toString(next.getValue()));
      }
    }
    assertTrue(scanner.seekBefore(toKV("k", tagUsage).getKey()));
    assertEquals("i", toRowStr(scanner.getKeyValue()));
    assertTrue(scanner.seekBefore(toKV("l", tagUsage).getKey()));
    assertEquals("k", toRowStr(scanner.getKeyValue()));

    reader.close();

    reader.close();
  }

  @Test
  public void testSeekBeforeWithReSeekTo() throws Exception {
    testSeekBeforeWithReSeekToInternals(TagUsage.NO_TAG);
    testSeekBeforeWithReSeekToInternals(TagUsage.ONLY_TAG);
    testSeekBeforeWithReSeekToInternals(TagUsage.PARTIAL_TAG);
  }

  protected void testSeekBeforeWithReSeekToInternals(TagUsage tagUsage) throws IOException {
    Path p = makeNewFile(tagUsage);
    FileSystem fs = TEST_UTIL.getTestFileSystem();
    Configuration conf = TEST_UTIL.getConfiguration();
    HFile.Reader reader = HFile.createReader(fs, p, new CacheConfig(conf), conf);
    reader.loadFileInfo();
    HFileScanner scanner = reader.getScanner(false, true);
    assertFalse(scanner.seekBefore(toKV("a", tagUsage).getKey()));
    assertFalse(scanner.seekBefore(toKV("b", tagUsage).getKey()));
    assertFalse(scanner.seekBefore(toKV("c", tagUsage).getKey()));

    // seekBefore d, so the scanner points to c
    assertTrue(scanner.seekBefore(toKV("d", tagUsage).getKey()));
    assertEquals("c", toRowStr(scanner.getKeyValue()));
    // reseekTo e and g
    assertEquals(0, scanner.reseekTo(toKV("c", tagUsage).getKey()));
    assertEquals("c", toRowStr(scanner.getKeyValue()));
    assertEquals(0, scanner.reseekTo(toKV("g", tagUsage).getKey()));
    assertEquals("g", toRowStr(scanner.getKeyValue()));

    // seekBefore e, so the scanner points to c
    assertTrue(scanner.seekBefore(toKV("e", tagUsage).getKey()));
    assertEquals("c", toRowStr(scanner.getKeyValue()));
    // reseekTo e and g
    assertEquals(0, scanner.reseekTo(toKV("e", tagUsage).getKey()));
    assertEquals("e", toRowStr(scanner.getKeyValue()));
    assertEquals(0, scanner.reseekTo(toKV("g", tagUsage).getKey()));
    assertEquals("g", toRowStr(scanner.getKeyValue()));

    // seekBefore f, so the scanner points to e
    assertTrue(scanner.seekBefore(toKV("f", tagUsage).getKey()));
    assertEquals("e", toRowStr(scanner.getKeyValue()));
    // reseekTo e and g
    assertEquals(0, scanner.reseekTo(toKV("e", tagUsage).getKey()));
    assertEquals("e", toRowStr(scanner.getKeyValue()));
    assertEquals(0, scanner.reseekTo(toKV("g", tagUsage).getKey()));
    assertEquals("g", toRowStr(scanner.getKeyValue()));

    // seekBefore g, so the scanner points to e
    assertTrue(scanner.seekBefore(toKV("g", tagUsage).getKey()));
    assertEquals("e", toRowStr(scanner.getKeyValue()));
    // reseekTo e and g again
    assertEquals(0, scanner.reseekTo(toKV("e", tagUsage).getKey()));
    assertEquals("e", toRowStr(scanner.getKeyValue()));
    assertEquals(0, scanner.reseekTo(toKV("g", tagUsage).getKey()));
    assertEquals("g", toRowStr(scanner.getKeyValue()));

    // seekBefore h, so the scanner points to g
    assertTrue(scanner.seekBefore(toKV("h", tagUsage).getKey()));
    assertEquals("g", toRowStr(scanner.getKeyValue()));
    // reseekTo g
    assertEquals(0, scanner.reseekTo(toKV("g", tagUsage).getKey()));
    assertEquals("g", toRowStr(scanner.getKeyValue()));

    // seekBefore i, so the scanner points to g
    assertTrue(scanner.seekBefore(toKV("i", tagUsage).getKey()));
    assertEquals("g", toRowStr(scanner.getKeyValue()));
    // reseekTo g
    assertEquals(0, scanner.reseekTo(toKV("g", tagUsage).getKey()));
    assertEquals("g", toRowStr(scanner.getKeyValue()));

    // seekBefore j, so the scanner points to i
    assertTrue(scanner.seekBefore(toKV("j", tagUsage).getKey()));
    assertEquals("i", toRowStr(scanner.getKeyValue()));
    // reseekTo i
    assertEquals(0, scanner.reseekTo(toKV("i", tagUsage).getKey()));
    assertEquals("i", toRowStr(scanner.getKeyValue()));

    // seekBefore k, so the scanner points to i
    assertTrue(scanner.seekBefore(toKV("k", tagUsage).getKey()));
    assertEquals("i", toRowStr(scanner.getKeyValue()));
    // reseekTo i and k
    assertEquals(0, scanner.reseekTo(toKV("i", tagUsage).getKey()));
    assertEquals("i", toRowStr(scanner.getKeyValue()));
    assertEquals(0, scanner.reseekTo(toKV("k", tagUsage).getKey()));
    assertEquals("k", toRowStr(scanner.getKeyValue()));

    // seekBefore l, so the scanner points to k
    assertTrue(scanner.seekBefore(toKV("l", tagUsage).getKey()));
    assertEquals("k", toRowStr(scanner.getKeyValue()));
    // reseekTo k
    assertEquals(0, scanner.reseekTo(toKV("k", tagUsage).getKey()));
    assertEquals("k", toRowStr(scanner.getKeyValue()));
    deleteTestDir(fs);
  }

  protected void deleteTestDir(FileSystem fs) throws IOException {
    Path dataTestDir = TEST_UTIL.getDataTestDir();
    if (fs.exists(dataTestDir)) {
      fs.delete(dataTestDir, true);
    }
       }
  @Test
  public void testSeekTo() throws Exception {
    testSeekToInternals(TagUsage.NO_TAG);
    testSeekToInternals(TagUsage.ONLY_TAG);
    testSeekToInternals(TagUsage.PARTIAL_TAG);
  }

  protected void testSeekToInternals(TagUsage tagUsage) throws IOException {
    Path p = makeNewFile(tagUsage);
    FileSystem fs = TEST_UTIL.getTestFileSystem();
    Configuration conf = TEST_UTIL.getConfiguration();
    HFile.Reader reader = HFile.createReader(fs, p, new CacheConfig(conf), conf);
    reader.loadFileInfo();
    assertEquals(2, reader.getDataBlockIndexReader().getRootBlockCount());
    HFileScanner scanner = reader.getScanner(false, true);
    // lies before the start of the file.
    assertEquals(-1, scanner.seekTo(toKV("a", tagUsage).getKey()));

    assertEquals(1, scanner.seekTo(toKV("d", tagUsage).getKey()));
    assertEquals("c", toRowStr(scanner.getKeyValue()));

    // Across a block boundary now.
    assertEquals(0, scanner.seekTo(toKV("i", tagUsage).getKey()));
    assertEquals("i", toRowStr(scanner.getKeyValue()));

    assertEquals(1, scanner.seekTo(toKV("l", tagUsage).getKey()));
    if (encoding == DataBlockEncoding.PREFIX_TREE) {
      // TODO : Fix this
      assertEquals(null, scanner.getKeyValue());
    } else {
      assertEquals("k", toRowStr(scanner.getKeyValue()));
    }

    reader.close();
  }

  @Test
  public void testBlockContainingKey() throws Exception {
    testBlockContainingKeyInternals(TagUsage.NO_TAG);
    testBlockContainingKeyInternals(TagUsage.ONLY_TAG);
    testBlockContainingKeyInternals(TagUsage.PARTIAL_TAG);
  }

  protected void testBlockContainingKeyInternals(TagUsage tagUsage) throws IOException {
    Path p = makeNewFile(tagUsage);
    FileSystem fs = TEST_UTIL.getTestFileSystem();
    Configuration conf = TEST_UTIL.getConfiguration();
    HFile.Reader reader = HFile.createReader(fs, p, new CacheConfig(conf), conf);
    reader.loadFileInfo();
    HFileBlockIndex.BlockIndexReader blockIndexReader = 
      reader.getDataBlockIndexReader();
    System.out.println(blockIndexReader.toString());
    int klen = toKV("a", tagUsage).getKey().length;
    // falls before the start of the file.
    assertEquals(-1, blockIndexReader.rootBlockContainingKey(
        toKV("a", tagUsage).getKey(), 0, klen));
    assertEquals(0, blockIndexReader.rootBlockContainingKey(
        toKV("c", tagUsage).getKey(), 0, klen));
    assertEquals(0, blockIndexReader.rootBlockContainingKey(
        toKV("d", tagUsage).getKey(), 0, klen));
    assertEquals(0, blockIndexReader.rootBlockContainingKey(
        toKV("e", tagUsage).getKey(), 0, klen));
    assertEquals(0, blockIndexReader.rootBlockContainingKey(
        toKV("g", tagUsage).getKey(), 0, klen));
    assertEquals(1, blockIndexReader.rootBlockContainingKey(
        toKV("h", tagUsage).getKey(), 0, klen));
    assertEquals(1, blockIndexReader.rootBlockContainingKey(
        toKV("i", tagUsage).getKey(), 0, klen));
    assertEquals(1, blockIndexReader.rootBlockContainingKey(
        toKV("j", tagUsage).getKey(), 0, klen));
    assertEquals(1, blockIndexReader.rootBlockContainingKey(
        toKV("k", tagUsage).getKey(), 0, klen));
    assertEquals(1, blockIndexReader.rootBlockContainingKey(
        toKV("l", tagUsage).getKey(), 0, klen));

    reader.close();
    deleteTestDir(fs);
  }


}

