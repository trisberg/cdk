/**
 * Copyright 2013 Cloudera Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cloudera.cdk.data.hbase.avro;

import com.cloudera.cdk.data.Dataset;
import com.cloudera.cdk.data.DatasetAccessor;
import com.cloudera.cdk.data.DatasetDescriptor;
import com.cloudera.cdk.data.PartitionKey;
import com.cloudera.cdk.data.PartitionStrategy;
import com.cloudera.cdk.data.hbase.HBaseDatasetRepository;
import com.cloudera.cdk.data.hbase.avro.entities.CompositeEntity;
import com.cloudera.cdk.data.hbase.avro.entities.SubEntity1;
import com.cloudera.cdk.data.hbase.avro.entities.SubEntity2;
import com.cloudera.cdk.data.hbase.avro.impl.AvroUtils;
import com.cloudera.cdk.data.hbase.testing.HBaseTestUtils;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

public class CompositeDatasetTest {

  private static final String subEntity1String;
  private static final String subEntity2String;
  private static final String tableName = "testtable";

  static {
    try {
      subEntity1String = AvroUtils.inputStreamToString(AvroDaoTest.class
          .getResourceAsStream("/SubEntity1.avsc"));
      subEntity2String = AvroUtils.inputStreamToString(AvroDaoTest.class
          .getResourceAsStream("/SubEntity2.avsc"));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @BeforeClass
  public static void beforeClass() throws Exception {
    HBaseTestUtils.getMiniCluster();
    byte[] tableNameBytes = Bytes.toBytes(tableName);
    byte[][] cfNames = { Bytes.toBytes("meta"), Bytes.toBytes("conflict"),
        Bytes.toBytes("_s") };
    HBaseTestUtils.util.createTable(tableNameBytes, cfNames);
  }

  @AfterClass
  public static void afterClass() throws Exception {
    HBaseTestUtils.util.deleteTable(Bytes.toBytes(tableName));
  }

  @Test
  public void testSpecific() throws Exception {

    HBaseDatasetRepository repo = new HBaseDatasetRepository.Builder()
        .configuration(HBaseTestUtils.getConf()).get();

    // create constituent datasets
    repo.create(tableName + ".SubEntity1", new DatasetDescriptor.Builder()
        .schema(SubEntity1.SCHEMA$)
        .get());
    repo.create(tableName + ".SubEntity2", new DatasetDescriptor.Builder()
        .schema(SubEntity2.SCHEMA$)
        .get());

    // create composite dataset
    DatasetDescriptor descriptor = new DatasetDescriptor.Builder()
        .schema(CompositeEntity.SCHEMA$)
        .get();
    Dataset ds = repo.create(tableName + ".CompositeEntity", descriptor);
    DatasetAccessor<CompositeEntity> accessor = ds.newAccessor();

    // Construct entities
    SubEntity1 subEntity1 = SubEntity1.newBuilder().setPart1("1").setPart2("1")
        .setField1("field1_1").setField2("field1_2").build();
    SubEntity2 subEntity2 = SubEntity2.newBuilder().setPart1("1").setPart2("1")
        .setField1("field2_1").setField2("field2_2").build();

    CompositeEntity compositeEntity = CompositeEntity.newBuilder()
        .setSubEntity1(subEntity1).setSubEntity2(subEntity2).build();

    // Test put and get
    accessor.put(compositeEntity);

    PartitionKey key = ds.getDescriptor().getPartitionStrategy().partitionKey("1", "1");
    CompositeEntity returnedCompositeEntity = accessor.get(key);
    assertNotNull("found entity", returnedCompositeEntity);
    assertEquals("field1_1", returnedCompositeEntity.getSubEntity1().getField1());
    assertEquals("field1_2", returnedCompositeEntity.getSubEntity1().getField2());
    assertEquals("field2_1", returnedCompositeEntity.getSubEntity2().getField1());
    assertEquals("field2_2", returnedCompositeEntity.getSubEntity2().getField2());

    // Test OCC
    assertFalse(accessor.put(compositeEntity));
    assertTrue(accessor.put(returnedCompositeEntity));

    // Test null field
    subEntity1 = SubEntity1.newBuilder(subEntity1).setPart2("2").build(); // different key
    compositeEntity = CompositeEntity.newBuilder().setSubEntity1(subEntity1).build();
    accessor.put(compositeEntity);
    returnedCompositeEntity = accessor.get(ds.getDescriptor().getPartitionStrategy().partitionKey("1", "2"));
    assertNull(returnedCompositeEntity.getSubEntity2());
  }
}
