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
package org.apache.hadoop.hbase;

import org.apache.yetus.audience.InterfaceAudience;

/**
 * POJO to present Orphan Region on RS from HBCK Inconsistencies Report via REST API. These
 * inconsistencies are shown on hbck.jsp page on Active HMaster UI as part of HBCK Inconsistencies.
 */
@InterfaceAudience.Public
public class HbckOrphanRegionsOnRS {
  private final String regionId;
  private final HbckServerName rsName;

  public HbckOrphanRegionsOnRS(String orphanRegionId, HbckServerName orphanRegionRsName) {
    this.regionId = orphanRegionId;
    this.rsName = orphanRegionRsName;
  }

  public String getRegionId() {
    return regionId;
  }

  public HbckServerName getRsName() {
    return rsName;
  }
}
