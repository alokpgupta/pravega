/**
 * Copyright (c) 2017 Dell Inc., or its subsidiaries. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.pravega.client.admin;

import io.pravega.client.ClientConfig;
import io.pravega.client.ClientFactory;
import io.pravega.client.admin.impl.ReaderGroupManagerImpl;
import io.pravega.client.netty.impl.ConnectionFactoryImpl;
import io.pravega.client.stream.ReaderConfig;
import io.pravega.client.stream.ReaderGroup;
import io.pravega.client.stream.ReaderGroupConfig;
import io.pravega.client.stream.Serializer;
import java.net.URI;
import java.util.Set;

/**
 * Used to create and manage reader groups.
 */
public interface ReaderGroupManager extends AutoCloseable {

    /**
     * Creates a new instance of ReaderGroupManager.
     *
     * @param scope The Scope string.
     * @param controllerUri The Controller URI.
     * @return Instance of Stream Manager implementation.
     */
    public static ReaderGroupManager withScope(String scope, URI controllerUri) {
        return withScope(scope, ClientConfig.builder().controllerURI(controllerUri).build());
    }

    /**
     * Creates a new instance of ReaderGroupManager.
     *
     * @param scope The Scope string.
     * @param clientConfig Configuration for the client.
     * @return Instance of Stream Manager implementation.
     */
    public static ReaderGroupManager withScope(String scope, ClientConfig clientConfig) {
        clientConfig = clientConfig.toBuilder().extractCredentials().build();
        return new ReaderGroupManagerImpl(scope, clientConfig, new ConnectionFactoryImpl(clientConfig));
    }

    /**
     * Creates a new ReaderGroup
     * 
     * Readers will be able to join the group by calling
     * {@link ClientFactory#createReader(String, String, Serializer, ReaderConfig)}
     * . Once this is done they will start receiving events from the point defined in the config
     * passed here.
     * <p>
     * Note: This method is idempotent assuming called with the same name and config. This method
     * may block.
     * 
     * @param groupName The name of the group to be created.
     * @param config The configuration for the new ReaderGroup.
     * @param streamNames The name of the streams the reader will read from.
     * @return Newly created ReaderGroup object
     */
    ReaderGroup createReaderGroup(String groupName, ReaderGroupConfig config, Set<String> streamNames);
    
    /**
     * Deletes a reader group, removing any state associated with it. There should be no reader left
     * on the group when this is called. If there are any, the group will be deleted from underneath
     * them and they will encounter exceptions.
     * 
     * @param groupName The group to be deleted.
     */
    void deleteReaderGroup(String groupName);
    
    /**
     * Returns the requested reader group.
     * 
     * @param groupName The name of the group
     * @return Reader group with the given name
     */
    ReaderGroup getReaderGroup(String groupName);
    
    /**
     * Close this manager class. This will close any connections created through it.
     * @see java.lang.AutoCloseable#close()
     */
    @Override
    void close();
    
}
