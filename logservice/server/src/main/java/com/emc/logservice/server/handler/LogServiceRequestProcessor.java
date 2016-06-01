package com.emc.logservice.server.handler;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiFunction;

import com.emc.logservice.contracts.ReadResult;
import com.emc.logservice.contracts.SegmentProperties;
import com.emc.logservice.contracts.StreamSegmentExistsException;
import com.emc.logservice.contracts.StreamSegmentNotExistsException;
import com.emc.logservice.contracts.StreamSegmentSealedException;
import com.emc.logservice.contracts.StreamSegmentStore;
import com.emc.logservice.contracts.WrongHostException;
import com.emc.nautilus.common.netty.FailingRequestProcessor;
import com.emc.nautilus.common.netty.RequestProcessor;
import com.emc.nautilus.common.netty.ServerConnection;
import com.emc.nautilus.common.netty.WireCommands.CreateSegment;
import com.emc.nautilus.common.netty.WireCommands.GetStreamSegmentInfo;
import com.emc.nautilus.common.netty.WireCommands.NoSuchSegment;
import com.emc.nautilus.common.netty.WireCommands.ReadSegment;
import com.emc.nautilus.common.netty.WireCommands.SegmentAlreadyExists;
import com.emc.nautilus.common.netty.WireCommands.SegmentCreated;
import com.emc.nautilus.common.netty.WireCommands.SegmentIsSealed;
import com.emc.nautilus.common.netty.WireCommands.StreamSegmentInfo;
import com.emc.nautilus.common.netty.WireCommands.WrongHost;

public class LogServiceRequestProcessor extends FailingRequestProcessor implements RequestProcessor {

	private static final Duration TIMEOUT = Duration.ofMinutes(1);

	private final StreamSegmentStore segmentStore;

	private final ServerConnection connection;

	public LogServiceRequestProcessor(StreamSegmentStore segmentStore, ServerConnection connection) {
		this.segmentStore = segmentStore;
		this.connection = connection;
	}

	@Override
	public void readSegment(ReadSegment readSegment) {
		CompletableFuture<ReadResult> future = segmentStore
			.read(readSegment.getSegment(), readSegment.getOffset(), readSegment.getSuggestedLength(), TIMEOUT);
		future.handle(new BiFunction<ReadResult, Throwable, Void>() {
			@Override
			public Void apply(ReadResult t, Throwable u) {
				// TODO: Return data...
				// This really should stream the data out in multiple results as
				// it is available.
				return null;
			}
		});
	}

	@Override
	public void getStreamSegmentInfo(GetStreamSegmentInfo getStreamSegmentInfo) {
		String segmentName = getStreamSegmentInfo.getSegmentName();
		CompletableFuture<SegmentProperties> future = segmentStore.getStreamSegmentInfo(segmentName, TIMEOUT);
		future.handle(new BiFunction<SegmentProperties, Throwable, Void>() {
			@Override
			public Void apply(SegmentProperties properties, Throwable u) {
				if (properties != null) {
					StreamSegmentInfo result = new StreamSegmentInfo(properties.getName(),
							true,
							properties.isSealed(),
							properties.isDeleted(),
							properties.getLastModified().getTime(),
							properties.getLength());
					connection.send(result);
				} else {
					connection.send(new StreamSegmentInfo(segmentName, false, true, true, 0, 0));
				}
				return null;
			}

		});
	}

	@Override
	public void createSegment(CreateSegment createStreamsSegment) {
		CompletableFuture<Void> future = segmentStore.createStreamSegment(createStreamsSegment.getSegment(), TIMEOUT);
		future.handle(new BiFunction<Void, Throwable, Void>() {
			@Override
			public Void apply(Void t, Throwable u) {
				if (u == null) {
					connection.send(new SegmentCreated(createStreamsSegment.getSegment()));
				} else {
					handleException(createStreamsSegment.getSegment(), u);
				}
				return null;
			}
		});
	}

	// TODO: Duplicated in AppendProcessor.
	private void handleException(String segment, Throwable u) {
		if (u == null) {
			throw new IllegalStateException("Neither offset nor exception!?");
		}
		if (u instanceof CompletionException) {
			u = u.getCause();
		}
		if (u instanceof StreamSegmentExistsException) {
			connection.send(new SegmentAlreadyExists(segment));
		} else if (u instanceof StreamSegmentNotExistsException) {
			connection.send(new NoSuchSegment(segment));
		} else if (u instanceof StreamSegmentSealedException) {
			connection.send(new SegmentIsSealed(segment));
		} else if (u instanceof WrongHostException) {
			WrongHostException wrongHost = (WrongHostException) u;
			connection.send(new WrongHost(wrongHost.getStreamSegmentName(), wrongHost.getCorrectHost()));
		} else {
			// TODO: don't know what to do here...
			connection.drop();
			throw new IllegalStateException("Unknown exception.", u);
		}
	}

	//
	// @Override
	// public void createBatch(CreateBatch createBatch) {
	// getNextRequestProcessor().createBatch(createBatch);
	// }
	//
	// @Override
	// public void mergeBatch(MergeBatch mergeBatch) {
	// getNextRequestProcessor().mergeBatch(mergeBatch);
	// }
	//
	// @Override
	// public void sealSegment(SealSegment sealSegment) {
	// getNextRequestProcessor().sealSegment(sealSegment);
	// }
	//
	// @Override
	// public void deleteSegment(DeleteSegment deleteSegment) {
	// getNextRequestProcessor().deleteSegment(deleteSegment);
	// }

}
