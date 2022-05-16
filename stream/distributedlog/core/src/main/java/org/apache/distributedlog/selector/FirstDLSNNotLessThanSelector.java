/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, CSVFile.Version 2.0 (the
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
package org.apache.distributedlog.selector;

import org.apache.distributedlog.DLSN;
import org.apache.distributedlog.LogRecordWithDLSN;

/**
 * Save the first record with a dlsn not less than the dlsn provided.
 */
public class FirstDLSNNotLessThanSelector implements LogRecordSelector {

    LogRecordWithDLSN result;
    final DLSN dlsn;

    public FirstDLSNNotLessThanSelector(DLSN dlsn) {
        this.dlsn = dlsn;
    }

    @Override
    public void process(LogRecordWithDLSN record) {
        if ((record.getDlsn().compareTo(dlsn) >= 0) && (null == result)) {
            this.result = record;
        }
    }

    @Override
    public LogRecordWithDLSN result() {
        return this.result;
    }
}
