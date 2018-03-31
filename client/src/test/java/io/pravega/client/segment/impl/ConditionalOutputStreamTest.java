/**
 * Copyright (c) 2017 Dell Inc., or its subsidiaries. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.pravega.client.segment.impl;

import io.pravega.client.netty.impl.ClientConnection;
import io.pravega.client.stream.mock.MockConnectionFactoryImpl;
import io.pravega.client.stream.mock.MockController;
import io.pravega.shared.protocol.netty.ConnectionFailedException;
import io.pravega.shared.protocol.netty.PravegaNodeUri;
import io.pravega.shared.protocol.netty.ReplyProcessor;
import io.pravega.shared.protocol.netty.WireCommands;
import io.pravega.shared.protocol.netty.WireCommands.AppendSetup;
import io.pravega.shared.protocol.netty.WireCommands.ConditionalAppend;
import io.pravega.shared.protocol.netty.WireCommands.SetupAppend;
import io.pravega.test.common.AssertExtensions;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;

public class ConditionalOutputStreamTest {

    @Test(timeout = 5000)
    public void testWrite() throws ConnectionFailedException, SegmentSealedException {
        MockConnectionFactoryImpl connectionFactory = new MockConnectionFactoryImpl();
        MockController controller = new MockController("localhost", 0, connectionFactory);
        ConditionalOutputStreamFactory factory = new ConditionalOutputStreamFactoryImpl(controller, connectionFactory);
        Segment segment = new Segment("scope", "testWrite", 1);       
        ConditionalOutputStream cOut = factory.createConditionalOutputStream(segment, "token");
        ByteBuffer data = ByteBuffer.allocate(10);
        
        ClientConnection mock = Mockito.mock(ClientConnection.class);
        PravegaNodeUri location = new PravegaNodeUri("localhost", 0);
        connectionFactory.provideConnection(location, mock);
        Mockito.doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                SetupAppend argument = (SetupAppend) invocation.getArgument(0);
                connectionFactory.getProcessor(location)
                                 .process(new AppendSetup(argument.getRequestId(), segment.getScopedName(),
                                                              argument.getWriterId(), 0));
                return null;
            }
        }).when(mock).send(any(SetupAppend.class));
        Mockito.doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                ConditionalAppend argument = (ConditionalAppend) invocation.getArgument(0);
                ReplyProcessor processor = connectionFactory.getProcessor(location);
                if (argument.getExpectedOffset() == 0 || argument.getExpectedOffset() == 2) {
                    processor.process(new WireCommands.DataAppended(argument.getWriterId(), argument.getEventNumber(), 0));
                } else { 
                    processor.process(new WireCommands.ConditionalCheckFailed(argument.getWriterId(), argument.getEventNumber()));
                }
                return null;
            }
        }).when(mock).send(any(ConditionalAppend.class));
        assertTrue(cOut.write(data, 0));
        assertFalse(cOut.write(data, 1));
        assertTrue(cOut.write(data, 2));
        assertFalse(cOut.write(data, 3));
    }
    
    @Test
    public void testClose() throws SegmentSealedException {
        MockConnectionFactoryImpl connectionFactory = new MockConnectionFactoryImpl();
        MockController controller = new MockController("localhost", 0, connectionFactory);
        ConditionalOutputStreamFactory factory = new ConditionalOutputStreamFactoryImpl(controller, connectionFactory);
        Segment segment = new Segment("scope", "testWrite", 1);       
        ConditionalOutputStream cOut = factory.createConditionalOutputStream(segment, "token");
        cOut.close();
        AssertExtensions.assertThrows(IllegalStateException.class, () -> cOut.write(ByteBuffer.allocate(0), 0));
    }
    
    @Test
    public void testRetries() throws ConnectionFailedException, SegmentSealedException {
        MockConnectionFactoryImpl connectionFactory = new MockConnectionFactoryImpl();
        MockController controller = new MockController("localhost", 0, connectionFactory);
        ConditionalOutputStreamFactory factory = new ConditionalOutputStreamFactoryImpl(controller, connectionFactory);
        Segment segment = new Segment("scope", "testWrite", 1);       
        ConditionalOutputStream cOut = factory.createConditionalOutputStream(segment, "token");
        ByteBuffer data = ByteBuffer.allocate(10);
        
        ClientConnection mock = Mockito.mock(ClientConnection.class);
        PravegaNodeUri location = new PravegaNodeUri("localhost", 0);
        connectionFactory.provideConnection(location, mock);
        Mockito.doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                SetupAppend argument = (SetupAppend) invocation.getArgument(0);
                connectionFactory.getProcessor(location)
                                 .process(new AppendSetup(argument.getRequestId(), segment.getScopedName(),
                                                              argument.getWriterId(), 0));
                return null;
            }
        }).when(mock).send(any(SetupAppend.class));
        final AtomicLong count = new AtomicLong(0);
        Mockito.doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                ConditionalAppend argument = (ConditionalAppend) invocation.getArgument(0);
                ReplyProcessor processor = connectionFactory.getProcessor(location);
                if (count.getAndIncrement() < 2) {
                    processor.connectionDropped();
                } else {
                    processor.process(new WireCommands.DataAppended(argument.getWriterId(), argument.getEventNumber(), 0));
                }                
                return null;
            }
        }).when(mock).send(any(ConditionalAppend.class));
        assertTrue(cOut.write(data, 0));
        assertEquals(3, count.get());
    }
    
    @Test(timeout = 10000)
    public void testSegmentSealed() throws ConnectionFailedException, SegmentSealedException {
        MockConnectionFactoryImpl connectionFactory = new MockConnectionFactoryImpl();
        MockController controller = new MockController("localhost", 0, connectionFactory);
        ConditionalOutputStreamFactory factory = new ConditionalOutputStreamFactoryImpl(controller, connectionFactory);
        Segment segment = new Segment("scope", "testWrite", 1);       
        ConditionalOutputStream cOut = factory.createConditionalOutputStream(segment, "token");
        ByteBuffer data = ByteBuffer.allocate(10);
        
        ClientConnection mock = Mockito.mock(ClientConnection.class);
        PravegaNodeUri location = new PravegaNodeUri("localhost", 0);
        connectionFactory.provideConnection(location, mock);
        Mockito.doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                SetupAppend argument = (SetupAppend) invocation.getArgument(0);
                connectionFactory.getProcessor(location)
                                 .process(new AppendSetup(argument.getRequestId(), segment.getScopedName(),
                                                              argument.getWriterId(), 0));
                return null;
            }
        }).when(mock).send(any(SetupAppend.class));
        Mockito.doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                ConditionalAppend argument = (ConditionalAppend) invocation.getArgument(0);
                ReplyProcessor processor = connectionFactory.getProcessor(location);
                processor.process(new WireCommands.SegmentIsSealed(argument.getEventNumber(), segment.getScopedName()));
                return null;
            }
        }).when(mock).send(any(ConditionalAppend.class));
        AssertExtensions.assertThrows(SegmentSealedException.class, () -> cOut.write(data, 0));
    }
    
}