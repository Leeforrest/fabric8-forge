/**
 *  Copyright 2005-2015 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */
package io.fabric8.forge.camel.commands.project.helper;

import io.fabric8.forge.camel.commands.project.model.CamelEndpointDetails;

import java.util.List;

public class CamelEndpoints {

    public static CamelEndpointDetails getEndpointDetailByInstanceName(Iterable<CamelEndpointDetails> endpointList, String instanceName) {
        for (CamelEndpointDetails detail : endpointList) {
            String endpointInstance = detail.getEndpointInstance();
            if (endpointInstance != null && endpointInstance.equals(instanceName)) {
                return detail;
            }
        }
        return null;
    }

    public static String createDefaultNewInstanceName(List<CamelEndpointDetails> endpointList) {
        int count = endpointList.size() + 1;
        while (count > 0 && count < Integer.MAX_VALUE) {
            String name = "endpoint" + count;
            if (getEndpointDetailByInstanceName(endpointList, name) == null) {
                return name;
            }
            count++;
        }
        return null;
    }
}
