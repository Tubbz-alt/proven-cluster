/*******************************************************************************
 * Copyright (c) 2017, Battelle Memorial Institute All rights reserved.
 * Battelle Memorial Institute (hereinafter Battelle) hereby grants permission to any person or entity 
 * lawfully obtaining a copy of this software and associated documentation files (hereinafter the 
 * Software) to redistribute and use the Software in source and binary forms, with or without modification. 
 * Such person or entity may use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of 
 * the Software, and may permit others to do so, subject to the following conditions:
 * Redistributions of source code must retain the above copyright notice, this list of conditions and the 
 * following disclaimers.
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and 
 * the following disclaimer in the documentation and/or other materials provided with the distribution.
 * Other than as used herein, neither the name Battelle Memorial Institute or Battelle may be used in any 
 * form whatsoever without the express written consent of Battelle.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY 
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL 
 * BATTELLE OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, 
 * OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE 
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED 
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING 
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED 
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * General disclaimer for use with OSS licenses
 * 
 * This material was prepared as an account of work sponsored by an agency of the United States Government. 
 * Neither the United States Government nor the United States Department of Energy, nor Battelle, nor any 
 * of their employees, nor any jurisdiction or organization that has cooperated in the development of these 
 * materials, makes any warranty, express or implied, or assumes any legal liability or responsibility for 
 * the accuracy, completeness, or usefulness or any information, apparatus, product, software, or process 
 * disclosed, or represents that its use would not infringe privately owned rights.
 * 
 * Reference herein to any specific commercial product, process, or service by trade name, trademark, manufacturer, 
 * or otherwise does not necessarily constitute or imply its endorsement, recommendation, or favoring by the United 
 * States Government or any agency thereof, or Battelle Memorial Institute. The views and opinions of authors expressed 
 * herein do not necessarily state or reflect those of the United States Government or any agency thereof.
 * 
 * PACIFIC NORTHWEST NATIONAL LABORATORY operated by BATTELLE for the 
 * UNITED STATES DEPARTMENT OF ENERGY under Contract DE-AC05-76RL01830
 ******************************************************************************/
package gov.pnnl.proven.cluster.lib.module.disclosure;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.enterprise.concurrent.ManagedExecutorService;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.hazelcast.core.IQueue;

import gov.pnnl.proven.cluster.lib.disclosure.exchange.BufferedItemState;
import gov.pnnl.proven.cluster.lib.disclosure.exchange.DisclosureProxy;
import gov.pnnl.proven.cluster.lib.module.component.ComponentStatus;
import gov.pnnl.proven.cluster.lib.module.component.event.StatusReport;
import gov.pnnl.proven.cluster.lib.module.disclosure.exception.EntryParserException;
import gov.pnnl.proven.cluster.lib.module.exchange.DisclosureBuffer;
import gov.pnnl.proven.cluster.lib.module.exchange.RequestExchange;
import gov.pnnl.proven.cluster.lib.module.exchange.exception.DisclosureEntryInterruptedException;

/**
 * A managed component representing a data structure to accept/store externally
 * provided disclosure messages, providing back pressure for its associated
 * {@code DisclosureBuffer}. Disclosure entries are forwarded as
 * {@code DisclosureProxy} objects to its disclosure buffer, if it has
 * processing capacity to support it.
 * 
 * @author d3j766
 * 
 * @see RequestExchange
 *
 */
public class DisclosureEntries extends DisclosureComponent {

	static Logger log = LoggerFactory.getLogger(DisclosureEntries.class);

	public static final String EXTERNAL_DISCLOSURE_QUEUE_NAME = "gov.pnnl.proven.external.disclosure";
	public static final String INTERNAL_LARGE_MESSAGE_QUEUE_NAME = "gov.pnnl.proven.internal.disclosure";
	public static final int MAX_DISCLOSURE_RETRIES = 10;

	IQueue<String> queue;
	IQueue<String> largeMessageQueue;
	CompletableFuture<Void> reader = CompletableFuture.completedFuture(null);
	CompletableFuture<Void> largeMessageReader = CompletableFuture.completedFuture(null);

	DisclosureBuffer localDisclosure;

	@Resource(lookup = RequestExchange.RE_EXECUTOR_SERVICE)
	ManagedExecutorService mes;

	@PostConstruct
	public void init() {
		log.debug("Post construct for DisclosureEntries");

		// TODO remove hardcoded name until multiple exchanges are supported
		// bufferSource = hzi.getQueue(getDistributedName());
		queue = hzi.getQueue(EXTERNAL_DISCLOSURE_QUEUE_NAME);
		largeMessageQueue = hzi.getQueue(INTERNAL_LARGE_MESSAGE_QUEUE_NAME);
		startReader(false);
		startLargeMessageReader(false);
	}

	@PreDestroy
	public void destroy() {
		log.debug("Pre destroy for DisclosureEntries");
		cancelReader();
		cancelLargeMessageReader();
	}

	@Inject
	public DisclosureEntries(InjectionPoint ip) {
		super();
		log.debug("DefaultConstructer for DisclosureBuffer");
	}

	private void startReader(boolean replace) {

		synchronized (reader) {

			boolean hasReader = ((null != reader) && (!reader.isDone()));

			// Remove existing reader if replace reader is requested
			if (hasReader && replace) {
				reader.cancel(true);
				hasReader = false;
			}

			// Add new reader if one doesn't exist
			if (!hasReader) {
				reader = CompletableFuture.runAsync(this::runReader, mes).exceptionally(this::readerException);
			}
		}

	}

	private void startLargeMessageReader(boolean replace) {

		synchronized (largeMessageReader) {

			boolean hasReader = ((null != largeMessageReader) && (!largeMessageReader.isDone()));

			// Remove existing reader if replace reader is requested
			if (hasReader && replace) {
				largeMessageReader.cancel(true);
				hasReader = false;
			}

			// Add new reader if one doesn't exist
			if (!hasReader) {
				largeMessageReader = CompletableFuture.runAsync(this::runLargeMessageReader, mes)
						.exceptionally(this::largeMessageReaderException);
			}
		}

	}

	// TODO provide exp backoff if disclosure buffer is full
	protected void runReader() {

		while (true) {

			List<DisclosureProxy> entries;

			// Should not continue if local disclosure has no free space - the
			// entries will be lost
			if ((null != localDisclosure) && (localDisclosure.hasFreeSpace(BufferedItemState.New))) {

				try {
					// Will block if queue is empty
					// entry = getEntry();
					entries = getEntries();

				} catch (InterruptedException e) {
					e.printStackTrace();
					Thread.currentThread().interrupt();
					throw new DisclosureEntryInterruptedException("Disclosure entires' queue reader interruped", e);
				}

				// Items are no longer in IMDG (queue reads are destructive)
				log.debug("ENTRIES BEING ADDED TO DISCLOSURE BUFFER: " + entries.size());

				boolean entriesAdded = true;
				if ((null != entries) && (!entries.isEmpty())) {
					BufferedItemState state = entries.get(0).getItemState();
					entriesAdded = localDisclosure.addItems(entries, state);
				}

				log.debug("ENTRY ADDED TO DISCLOSURE BUFFER");
				log.info("Queue size :: " + queue.size());

				// TODO implement fall backs instead of simply logging an error
				// message, or these entries will be lost.
				if (!entriesAdded) {
					// Overwrite failure - Disclosure Buffer is full.
					log.error("FAILED TO ADD DISCLOSURE ENTRIES TO DISCLOSURE BUFFER- ENTRIES LOST");
				}

			} else {
				log.info("Local disclosure buffer is not available");
			}
		}

	}

	/**
	 * Callback for entry reader that has ended exceptionally.
	 * 
	 * @param readerException
	 *            the exception thrown from the reader.
	 */
	protected Void readerException(Throwable readerException) {

		Void ret = null;

		boolean isInterrupted = (readerException instanceof DisclosureEntryInterruptedException);
		boolean isCancelled = (readerException instanceof CancellationException);

		// Interrupted exception - indicates the reader has failed due to thread
		// interruption, attempts to restart should be made.
		if (isInterrupted) {
			log.info("Disclosure entry reader was interrupted.");
			startReader(true);
		}

		// Cancelled exception - indicates the reader has been cancelled. This
		// is a controlled event, attempts to restart the reader should not be
		// made.
		else if (isCancelled) {
			log.info("Disclosure entry reader was cancelled.");
		}

		// Other exceptions - not managed, do not attempt to restart.
		else {
			log.info("Disclosure entry reader was completed exceptionally :: " + readerException.getMessage());
			readerException.printStackTrace();
		}

		return ret;
	}

	protected void runLargeMessageReader() {

		List<DisclosureProxy> entries = new ArrayList<>();
		boolean isRetry = false;
		int retriesLeft = MAX_DISCLOSURE_RETRIES;
		
		while (true) {

			String disclosedEntry = null;

			// Should not continue if local disclosure has no free space - the
			// entries will be lost. Free space is determined by max batch size
			// value, it's assumed that the max number of chunked/parsed
			// messages generated from one "large message" does not exceed this
			// value.
			if ((null != localDisclosure) && (localDisclosure.hasFreeSpace(BufferedItemState.New))) {

				try {

					// Process entry - if parse fails throw it away
					// TODO - failure reporting back to disclosure source -
					// in addition to console logging.
					if (!isRetry) {
						try {
							disclosedEntry = largeMessageQueue.peek();
							if (null != disclosedEntry) {
								entries = new EntryParser(disclosedEntry).parse();
							}
							else {
								// queue is empty incrementally backoff on peeks
								Thread.sleep(1000);
							}
						} catch (EntryParserException ex) {
							log.error("Large message parsing exception - removing entry");
							disclosedEntry = null;
							entries.clear();
							largeMessageQueue.take();
							ex.printStackTrace();
						}
					}
					
					if (!entries.isEmpty()) {

						log.debug("ENTRIES BEING ADDED TO DISCLOSURE BUFFER: " + entries.size());

						boolean entriesAdded = true;
						if ((null != entries) && (!entries.isEmpty())) {
							BufferedItemState state = entries.get(0).getItemState();
							entriesAdded = localDisclosure.addItems(entries, state);
						}

						log.debug("ENTRY ADDED TO DISCLOSURE BUFFER");
						log.info("Queue size :: " + queue.size());

						// Allow for retries, before giving up.
						// TODO -
						if (!entriesAdded) {
							isRetry = true;
							retriesLeft--;
							log.error("FAILED TO ADD DISCLOSURE ENTRIES, RETRIES LEFT: " + retriesLeft);
							if (retriesLeft <= 0) {
								disclosedEntry = null;
								entries.clear();
								largeMessageQueue.take();
								isRetry = false;
								retriesLeft = MAX_DISCLOSURE_RETRIES;
							}
						} else {
							isRetry = false;
							retriesLeft = MAX_DISCLOSURE_RETRIES;
							largeMessageQueue.take();
						}
					}

				} catch (InterruptedException e) {
					e.printStackTrace();
					Thread.currentThread().interrupt();
					throw new DisclosureEntryInterruptedException(
							"Disclosure entires' large message queue reader interruped", e);
				}

			} else {
				log.info("Local disclosure buffer is not available");
			}
		}
	}

	/**
	 * Callback for entry reader that has ended exceptionally.
	 * 
	 * @param readerException
	 *            the exception thrown from the reader.
	 */
	protected Void largeMessageReaderException(Throwable readerException) {

		Void ret = null;

		boolean isInterrupted = (readerException instanceof DisclosureEntryInterruptedException);
		boolean isCancelled = (readerException instanceof CancellationException);

		// Interrupted exception - indicates the reader has failed due to thread
		// interruption, attempts to restart should be made.
		if (isInterrupted) {
			log.info("Disclosure entry reader was interrupted.");
			startReader(true);
		}

		// Cancelled exception - indicates the reader has been cancelled. This
		// is a controlled event, attempts to restart the reader should not be
		// made.
		else if (isCancelled) {
			log.info("Disclosure entry reader was cancelled.");
		}

		// Other exceptions - not managed, do not attempt to restart.
		else {
			log.info("Disclosure entry reader was completed exceptionally :: " + readerException.getMessage());
			readerException.printStackTrace();
		}

		return ret;
	}

	public void cancelReader() {

		if (reader != null) {
			synchronized (reader) {
				if ((null != reader) && (!reader.isDone())) {
					reader.cancel(true);
				}
			}
		}
	}

	public void cancelLargeMessageReader() {

		if (largeMessageReader != null) {
			synchronized (largeMessageReader) {
				if ((null != largeMessageReader) && (!largeMessageReader.isDone())) {
					largeMessageReader.cancel(true);
				}
			}
		}
	}

	public List<DisclosureProxy> getEntries() throws InterruptedException {

		List<DisclosureProxy> entries = new ArrayList<>();

		// TODO - create a service that returns the initial disclosure state
		int max = localDisclosure.getMaxBatchSize(BufferedItemState.New);
		boolean done = false;
		boolean initialCheck = true;

		while (!done) {

			// Block on empty queue at startup
			if (initialCheck) {
				addEntry(entries, Optional.ofNullable(null));
				initialCheck = false;
			}

			// Poll for next item - timeout will flush up to maximum queue
			// entries. TODO - also flush entries if the entry exceed a max
			// size.
			int qSize = queue.size();
			int eSize = entries.size();
			if ((qSize + eSize) < max) {
				String entry = queue.poll(250, TimeUnit.MILLISECONDS);
				// Timeout occurred
				if (null == entry) {
					for (int i = 0; i < qSize - 1; i++) {
						addEntry(entries, Optional.ofNullable(null));
					}
					done = true;
				} else {
					addEntry(entries, Optional.ofNullable(entry));
				}
			}

			// Flush max entries
			else {
				for (int i = 0; i < qSize - 1; i++) {
					addEntry(entries, Optional.ofNullable(null));
				}
				done = true;
			}

		}
		log.debug("REMOVED ENTRIES FROM QUEUE :: " + entries.size());
		return entries;
	}

	/**
	 * Checks size of entry and if size is greater then the internal limit, then
	 * entry is forwarded to the "large message" queue. Otherwise, the entry is
	 * converted into a {@code DisclosureProxy} and added to the list of entries
	 * for return. This method will block on queue take call, if no entries are
	 * present.
	 * 
	 * TODO Resolve loss of large disclosure entries on offer failures.
	 * 
	 * @param entries
	 *            list of disclosure entries
	 * @param entry
	 *            the entry to include in entries or forward to to "large
	 *            message" queue if it violated internal sizing restriction.
	 * @throws InterruptedException
	 */
	private void addEntry(List<DisclosureProxy> entries, Optional<String> optEntry) throws InterruptedException {
		String entry = (optEntry.isPresent()) ? optEntry.get() : queue.take();
		if (entry.length() > EntryParser.MAX_INTERNAL_ENTRY_SIZE_CHARS) {
			if (!largeMessageQueue.offer(entry)) {
				log.error("Large message entry offer failed - message has been lost");
			}
		} else {
			entries.add(new DisclosureProxy(entry));
		}
	}

	public void addLocalDisclosure(DisclosureBuffer db) {
		localDisclosure = db;
	}

	@Override
	public void activate() {
		// TODO Auto-generated method stub

	}

	@Override
	public void deactivate() {
		// TODO Auto-generated method stub

	}

	@Override
	public StatusReport getStatusReport() {
		return null;

	}

	@Override
	public ComponentStatus getStatus() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setStatus(ComponentStatus status) {
		// TODO Auto-generated method stub

	}

}
