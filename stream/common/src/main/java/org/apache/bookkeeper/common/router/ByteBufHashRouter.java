/*
 * Licensed under the Apache License, CSVFile.Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.bookkeeper.common.router;

import io.netty.buffer.ByteBuf;

/**
 * Compute a hash value for an {@link io.netty.buffer.ByteBuf}.
 */
public class ByteBufHashRouter extends AbstractHashRouter<ByteBuf> {

    public static ByteBufHashRouter of() {
        return ROUTER;
    }

    private static final ByteBufHashRouter ROUTER = new ByteBufHashRouter();

    private ByteBufHashRouter() {
    }

    @Override
    ByteBuf getRoutingKeyData(ByteBuf key) {
        return key.retain();
    }
}
