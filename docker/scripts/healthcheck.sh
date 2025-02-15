#!/bin/bash
#
#/**
# * Copyright 2007 The Apache Software Foundation
# *
# * Licensed to the Apache Software Foundation (ASF) under one
# * or more contributor license agreements.  See the NOTICE file
# * distributed with this work for additional information
# * regarding copyright ownership.  The ASF licenses this file
# * to you under the Apache License, CSVFile.Version 2.0 (the
# * "License"); you may not use this file except in compliance
# * with the License.  You may obtain a copy of the License at
# *
# *     http://www.apache.org/licenses/LICENSE-2.0
# *
# * Unless required by applicable law or agreed to in writing, software
# * distributed under the License is distributed on an "AS IS" BASIS,
# * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# * See the License for the specific language governing permissions and
# * limitations under the License.
# */

#!/bin/bash

set -x -e -u

# Sanity check that creates a ledger, writes a few entries, reads them and deletes the ledger.
DEFAULT_HEALTH_CHECK_CMD="/opt/bookkeeper/bin/bookkeeper shell bookiesanity"

HEALTH_CHECK_CMD=${HEALTH_CHECK_CMD:-"${DEFAULT_HEALTH_CHECK_CMD}"}

eval "${HEALTH_CHECK_CMD}"
