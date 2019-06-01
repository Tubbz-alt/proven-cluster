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

import java.io.StringReader;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import java.util.UUID;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReaderFactory;
import javax.json.stream.JsonParser;
import javax.json.stream.JsonParserFactory;
import javax.json.stream.JsonParser.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import gov.pnnl.proven.cluster.lib.disclosure.exchange.DisclosureProxy;
import gov.pnnl.proven.cluster.lib.disclosure.message.MessageUtils;
import gov.pnnl.proven.cluster.lib.module.disclosure.exception.EntryParserException;

/**
 * 
 * 
 * @author d3j766
 *
 */
public class EntryParser {

	static Logger log = LoggerFactory.getLogger(EntryParser.class);

	private static final JsonParserFactory pFactory = Json.createParserFactory(null);
	private static final JsonReaderFactory rFactory = Json.createReaderFactory(null);
	private static final JsonBuilderFactory bFactory = Json.createBuilderFactory(null);

	// Message constants
	private static final String MESSAGE_KEY = "message";

	// Linked data constants
	private static final String LD_ID = "@id";

	// 20 internal messages per max external message (WS not included)
	public static final int MAX_INTERNAL_ENTRY_SIZE_CHARS = 250000; // 250K
	public static final int MAX_EXERNAL_ENTRY_SIZE_CHARS = 5000000; // 5M

	// entry builder types
	private enum EntryBuilderType {
		OBJECT,
		ARRAY;
	}

	private JsonObject entryContainer;
	private Optional<JsonObject> message = Optional.empty();
	private Optional<JsonParser> messageParser = Optional.empty();
	private Stack<EntryBuilder> unfinishedBuilders;

	private class EntryBuilder {

		private EntryBuilderType ebType;
		private JsonObjectBuilder ob;
		private JsonArrayBuilder ab;
		private String oId;
		private String oUri;
		private int size;
		private String lastKey;

		EntryBuilder(EntryBuilderType bType, int size) {
			this(bType, size, false);
		}

		EntryBuilder(EntryBuilderType ebType, int size, boolean isRoot) {

			// Create builder based on provided type
			this.ebType = ebType;
			if (ebType == EntryBuilderType.OBJECT) {

				oId = UUID.randomUUID().toString();
				if (isRoot) {
					oUri = MessageUtils.PROVEN_MESSAGE_CONTENT_NODE_RES + "_" + oId;
					ob.add(LD_ID, oUri);
				} else {
					oUri = MessageUtils.PROVEN_MESSAGE_CONTENT_CHILD_NODE_RES + "_" + oId;
					ob.add(LD_ID, oUri);
				}
				ob = bFactory.createObjectBuilder();
			} else {
				ab = bFactory.createArrayBuilder();
			}
			this.size = size;
			this.lastKey = null;
		}

		private void add(EntryBuilder uBuilder) {

			if (this.ebType == EntryBuilderType.OBJECT) {
				if (uBuilder.ebType == EntryBuilderType.OBJECT) {
					this.ob.add(this.lastKey, uBuilder.ob);
				} else {
					this.ob.add(this.lastKey, uBuilder.ab);
				}
			} else {
				if (uBuilder.ebType == EntryBuilderType.OBJECT) {
					this.ab.add(uBuilder.ob);
				} else {
					this.ab.add(uBuilder.ob);
				}
			}
		}

		private void reset() {

			size = MAX_INTERNAL_ENTRY_SIZE_CHARS;
			lastKey = null;
			if (ebType == EntryBuilderType.OBJECT) {
				ob = bFactory.createObjectBuilder();
			} else {
				ab = bFactory.createArrayBuilder();
			}
		}

	}

	public EntryParser(String entry) {

		try (JsonParser initParser = pFactory.createParser(new StringReader(entry))) {

			JsonObjectBuilder ecb = bFactory.createObjectBuilder();
			String lastKey = null;
			boolean isFirstEvent = true;

			while (initParser.hasNext()) {

				Event event = initParser.next();
				System.out.println("CURENT STATE : " + event.toString());

				switch (event) {

				case START_OBJECT:
					if (isFirstEvent) {
						isFirstEvent = false;
						String oUri = MessageUtils.PROVEN_MESSAGE_RES + "_" + UUID.randomUUID().toString();
						ecb.add(LD_ID, oUri);
					}
					if ((null != lastKey) && (lastKey.equals(MESSAGE_KEY))) {
						message = Optional.of(initParser.getObject());
					} else {
						initParser.skipObject();
					}
					break;

				case START_ARRAY:
					ecb.add(lastKey, initParser.getArray());
					break;

				case END_OBJECT:
					entryContainer = ecb.build();
					break;

				case KEY_NAME:
					lastKey = initParser.getString();
					break;

				case VALUE_STRING:
					String strVal = initParser.getString();
					ecb.add(lastKey, strVal);
					break;

				case VALUE_NUMBER:
					if (initParser.isIntegralNumber()) {
						long lVal = initParser.getLong();
						ecb.add(lastKey, lVal);
					} else {
						BigDecimal bdVal = initParser.getBigDecimal();
						ecb.add(lastKey, bdVal);
					}
					break;

				case VALUE_TRUE:
					ecb.add(lastKey, true);
					break;

				case VALUE_FALSE:
					ecb.add(lastKey, false);
					break;

				case VALUE_NULL:
					ecb.addNull(lastKey);
					break;

				default:
					throw new EntryParserException("Unknown structure processing event: " + event);
				}
			}

		} catch (Exception ex) {
			throw new EntryParserException("Error occurred parsing disclosure entry.", ex);
		}

		// Create message parser
		if (message.isPresent()) {
			messageParser = Optional.of(pFactory.createParser(message.get()));
		}

	}

	public List<DisclosureProxy> parse() {
		return parse(null);
	}

	private List<DisclosureProxy> parse(EntryBuilder eBuilder) {

		List<DisclosureProxy> ret = new ArrayList<>();
		boolean isRoot = false;
		boolean isFirstEvent = true;

		// root builder
		if (null == eBuilder) {
			isRoot = true;
			eBuilder = new EntryBuilder(EntryBuilderType.OBJECT, MAX_INTERNAL_ENTRY_SIZE_CHARS, true);
		}

		// Add to stack
		unfinishedBuilders.push(eBuilder);

		// There will only be a parser if the message is present, so no need
		// to test for message existence.
		if (messageParser.isPresent()) {

			try (JsonParser parser = messageParser.get()) {
						
			while (parser.hasNext()) {

				Event event = parser.next();
				System.out.println("CURENT STATE : " + event.toString());

				switch (event) {

				case START_OBJECT:
				case START_ARRAY:
					if (isRoot && isFirstEvent) {
						isFirstEvent = false;
					} else {
						if (event == Event.START_OBJECT) {
							ret.addAll(parse(new EntryBuilder(EntryBuilderType.OBJECT, eBuilder.size)));
						} else {
							ret.addAll(parse(new EntryBuilder(EntryBuilderType.ARRAY, eBuilder.size)));
						}

						// Child builder has finished. Add it's finished
						// structure and remaining size count to this builder
						// and then
						// remove it from the unfinished stack.
						EntryBuilder uBuilder = unfinishedBuilders.pop();
						eBuilder.add(uBuilder);
						eBuilder.size = uBuilder.size;
					}
					break;

				case END_OBJECT:
					if (isRoot) {
						ret.add(buildMessage());
					}
				case END_ARRAY:
					return ret;

				case KEY_NAME:
					eBuilder.lastKey = parser.getString();
					eBuilder.size = eBuilder.size - eBuilder.lastKey.length();
					break;

				case VALUE_STRING:
					String strVal = parser.getString();
					eBuilder.size = eBuilder.size - eBuilder.lastKey.length() - strVal.length();
					if (eBuilder.ebType == EntryBuilderType.OBJECT) {
						eBuilder.ob.add(eBuilder.lastKey, strVal);
					} else { // array
						eBuilder.ab.add(strVal);
					}
					break;

				case VALUE_NUMBER:
					if (parser.isIntegralNumber()) {
						long lVal = parser.getLong();
						long temp = lVal;
						int count = 0;
						if (lVal == 0)
							count = 1;
						else {
							while (temp != 0) {
								temp = temp / 10;
								++count;
							}
						}
						eBuilder.size = eBuilder.size - eBuilder.lastKey.length() - count;
						if (eBuilder.ebType == EntryBuilderType.OBJECT) {
							eBuilder.ob.add(eBuilder.lastKey, lVal);
						} else { // array
							eBuilder.ab.add(lVal);
						}
					} else {
						BigDecimal bdVal = parser.getBigDecimal();
						eBuilder.size = eBuilder.size - eBuilder.lastKey.length() - bdVal.toString().length();
						if (eBuilder.ebType == EntryBuilderType.OBJECT) {
							eBuilder.ob.add(eBuilder.lastKey, bdVal);
						} else { // array
							eBuilder.ab.add(bdVal);
						}
					}
					break;

				case VALUE_TRUE:
					eBuilder.size = eBuilder.size - eBuilder.lastKey.length() - 4;
					if (eBuilder.ebType == EntryBuilderType.OBJECT) {
						eBuilder.ob.add(eBuilder.lastKey, true);
					} else { // array
						eBuilder.ab.add(true);
					}
					break;

				case VALUE_FALSE:
					eBuilder.size = eBuilder.size - eBuilder.lastKey.length() - 4;
					if (eBuilder.ebType == EntryBuilderType.OBJECT) {
						eBuilder.ob.add(eBuilder.lastKey, false);
					} else { // array
						eBuilder.ab.add(false);
					}
					break;

				case VALUE_NULL:
					eBuilder.size = eBuilder.size - 4;
					if (eBuilder.ebType == EntryBuilderType.OBJECT) {
						eBuilder.ob.addNull(eBuilder.lastKey);
					} else { // array
						eBuilder.ab.addNull();
					}
					break;

				default:
					throw new EntryParserException("Unknown structure processing event: " + event);
				}

				// Check size if exceeded, build current message and add it to
				// the Set of return values. Must not end on KEY_NAME event, as
				// it would split key from value.
				if ((eBuilder.size < 0) && (event != Event.KEY_NAME)) {
					ret.add(buildMessage());
				}
			}
			
			}
			catch (Exception ex) {
				throw new EntryParserException("Error occurred parsing disclosure entry.", ex);
			}

		} else {
			// create new entry - no message content provided
			ret.add(new DisclosureProxy(entryContainer));
		}

		return ret;
	}

	private DisclosureProxy buildMessage() {

		DisclosureProxy ret = null;
		EntryBuilder temp;
		Stack<EntryBuilder> processedBuilders = new Stack<>();

		// Get current builder off stack - should never be null.
		EntryBuilder cBuilder = unfinishedBuilders.pop();

		// Get previous builder off stack - may be null.
		EntryBuilder pBuilder = unfinishedBuilders.pop();

		boolean done = false;
		while (!done) {

			// root object
			if (null == pBuilder) {
				JsonObjectBuilder messageBuilder = bFactory.createObjectBuilder(entryContainer);
				ret = new DisclosureProxy(messageBuilder.add(MESSAGE_KEY, cBuilder.ob).build());
				cBuilder.reset();
				processedBuilders.push(cBuilder);
				done = true;
			} else {
				pBuilder.add(cBuilder);
				temp = cBuilder;
				temp.reset();
				processedBuilders.push(temp);
				cBuilder = pBuilder;
				pBuilder = unfinishedBuilders.pop();
			}
		}

		// Move back to unfinished stack
		EntryBuilder builder = processedBuilders.pop();
		while (null != builder) {
			unfinishedBuilders.push(builder);
			builder = processedBuilders.pop();
		}

		return ret;
	}

}
