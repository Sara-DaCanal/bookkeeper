#!/usr/bin/env bash
#
#/**
# * Copyright 2016 The Apache Software Foundation
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
#
# This script is used as NetworkTopology Mapping Script in TestRackawareEnsemblePlacementPolicyUsingScript.java TestSuite
# It just maps HostAddress to rack depending on the last character of the HostAddress string
# for eg. 
#       127.0.0.1    - /1
#       127.0.0.2    - /2
#       199.12.34.21 - /1
# This script file is used just for testing purpose
# rack 0 returns script error (non-zero error code)

for var in "$@"
do
    i=$((${#var}-1))
    if [ "${var:$i:1}" == "0" ]; then
        exit 1 
    fi
    echo /${var:$i:1}
done