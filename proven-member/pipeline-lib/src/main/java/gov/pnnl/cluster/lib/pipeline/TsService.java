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
package gov.pnnl.cluster.lib.pipeline;

import java.io.File;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.Serializable;
import java.io.StringReader;
import java.lang.reflect.Array;
import java.util.concurrent.TimeUnit;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.ws.rs.core.Response;


import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.dto.QueryResult.Result;
import org.influxdb.dto.QueryResult.Series;
import org.influxdb.impl.TimeUtil;


import com.hazelcast.internal.json.JsonValue;
import com.hazelcast.jet.pipeline.ContextFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.pnnl.proven.cluster.lib.disclosure.message.MessageUtils;
import gov.pnnl.proven.cluster.lib.disclosure.message.ProvenMessage;
import gov.pnnl.proven.cluster.lib.disclosure.message.ResponseMessage;
import gov.pnnl.proven.cluster.lib.member.MemberProperties;


/**
 * Provides services to triple store (T3).
 * 
 * @author d3j766
 *
 */
public class TsService {

	private static Logger log = LoggerFactory.getLogger(TsService.class);

	
	private Long startTime = System.currentTimeMillis();
	private InfluxDB influxDB;
	private String serviceUrl = "http://127.0.0.1:8086";
	private Boolean useIdb = true;
	String idbDB = "proven";
	String idbRP = "autogen";
	String idbUsername =   "root";
	String idbPassword =  "root";
	

	private Jsonb jsonb = JsonbBuilder.create();

	/**
	 * Summary response information for a T3 storage request. This is included
	 * in a {@code ResponseMessage} as it's message content.
	 * 
	 * @author d3j766
	 *
	 */
	public class TsResponse implements Serializable {

		private static final long serialVersionUID = 1L;

		int statusCode;
		String statusReason;
		long count;
		String message;

		TsResponse() {
		}

		TsResponse(Response.Status status, long count) {
			this.statusCode = status.getStatusCode();
			this.statusReason = status.getReasonPhrase();
			this.count = count;
			this.message = "";
		}
	}


	/**
	 * Returns {@code ContextFactory} for Jet processing pipelines that require TS services.
	 * 
	 * @return {@link ContextFactory}
	 */
	public static ContextFactory<TsService> tsService() {

		return ContextFactory.withCreateFn(x -> TsService.newTsService()).toNonCooperative().withLocalSharing();
		
	}

		
	/**
	 * Creates a new T3Service with default settings.
	 * 
	 * @param serviceUrl
	 *            identifies SPARQL endpoint
	 * 
	 * @throws RepositoryException
	 */
	public static TsService newTsService()  {
		TsService tsS = new TsService();
		
		return tsS;
	}

	private TsService() {
		
//	    serviceUrl = props.getHybridTsServiceUrl();
//		useIdb = props.getHybridTsUseIdb();
//		idbDB = props.getHybridTsIdbDb(); 
//		idbRP = props.getHybridTsIdbRp();
//		idbUsername =   props.getHybridTsIdbUsername(); 
//		idbPassword =  props.getHybridTsIdbPassword();

	}

	// @Override
	public ResponseMessage add(ProvenMessage sourceMessage) {

		ResponseMessage ret = null;
		TsResponse loadResponse = null;

		JsonObjectBuilder ldbuilder = Json.createObjectBuilder();
        ldbuilder.add("id", "id");
        ldbuilder.add("date", "2020");
		JsonObject loadResponseObject = ldbuilder.build();
		ret = new ResponseMessage(Response.Status.CREATED, sourceMessage,
				loadResponseObject);
	
			// Construct initial data model
			JsonObject mObject = sourceMessage.getMessage();

			
			if (useIdb) {

				influxDB = InfluxDBFactory.connect(serviceUrl, idbUsername, idbPassword);
				influxDB.enableBatch(20000, 20, TimeUnit.SECONDS);

				long timestamp  = mObject.getJsonNumber("timestamp").longValue();
				String measurement = mObject.getJsonString("measurement").getString();
				JsonObject messageObject = mObject.getJsonObject("message");
				JsonArray metricsArray = messageObject.getJsonArray("metrics");
				int metricsSize = metricsArray.size();
				Point.Builder builder = Point.measurement(measurement).time(timestamp, TimeUnit.SECONDS);
				for (int i = 0; i < metricsSize; i++) {
//					JsonArray transaction = metricsArray.getJsonArray(i);
//					int transactionSize = transaction.size();
//					for (int ti = 0; ti < transactionSize ; ti++) {
						System.out.println("BEGIN POINT");
						JsonObject recordsObject = (JsonObject) metricsArray.getJsonObject(i);
						JsonArray recordValues = (JsonArray) recordsObject.getJsonArray("record");
						int valuesSize = recordValues.size();
						for (int vi = 0 ; vi < valuesSize ; vi++)  {
							String[] arrStr = recordValues.getString(vi).split(",");
							int x = vi;
							if (arrStr[0].equalsIgnoreCase("TRUE")) {
								builder.tag(arrStr[1], arrStr[2]);
								System.out.println("Tag Name= " + arrStr[1] + " Value= " + arrStr[2] + " Data Type= " +arrStr[3]);

							} else {
								System.out.println("Field Name= " + arrStr[1] + " Value= " + arrStr[2] + " Data Type= " +arrStr[3]);

								if (arrStr[3].equalsIgnoreCase("STRING")) {
									builder.addField(arrStr[1], arrStr[2]);
								}  else if (arrStr[3].equalsIgnoreCase("FLOAT")) {
									builder.addField(arrStr[1], Float.parseFloat(arrStr[2]));
								}  else if (arrStr[3].equalsIgnoreCase("INTEGER")) {
									builder.addField(arrStr[1], Integer.parseInt(arrStr[2]));
								}  else if (arrStr[3].equalsIgnoreCase("BOOLEAN")) {
									builder.addField(arrStr[1], Boolean.parseBoolean(arrStr[2]));									
								}  else if (arrStr[3].equalsIgnoreCase("LONG")) {
									builder.addField(arrStr[1], Long.parseLong(arrStr[2]));									
								}  else if (arrStr[3].equalsIgnoreCase("DOUBLE")) {
									builder.addField(arrStr[1], Double.parseDouble(arrStr[2]));									
								}
								
							}
							
						}
						System.out.println("END POINT");
						influxDB.write(idbDB, idbRP, builder.build());
						influxDB.flush();
					}

//				}
				try {
					// influxDB.write(idbDB, idbRP, builder.build());
//					ret = new ProvenMessageResponse();
//					ret.setReason("success");
//					ret.setStatus(Status.CREATED);
//					ret.setCode(Status.CREATED.getStatusCode());
//					ret.setResponse("{ \"INFO\": \"Time-series measurements successfully created.\" }");
			

				JsonReader  reader = Json.createReader(new StringReader(jsonb.toJson("{}")));
				loadResponseObject = reader.readObject();
				//JsonReader reader = Json.createReader(new StringReader(""));
				ret = new ResponseMessage(Response.Status.CREATED, sourceMessage,
						loadResponseObject);
				int i = 0;
				i = i + 1;
				return ret;
				} catch (Exception e) {
//					ret =  new ProvenMessageResponse();
//					ret.setReason("error");
//					ret.setStatus(Status.BAD_REQUEST);
//					ret.setCode(Status.BAD_REQUEST.getStatusCode());
//					ret.setResponse("{ \"ERROR\": \"Error interpreting measurement, possibly malformed JSON or no fields in measurement, output not recorded.\" }");
					return ret;

				}

			}
			
			
					
			
			
			
			
			
			
			
//			
//			
//			
//			
//			
//			//JsonReader reader = Json.createReader(new StringReader(""));
//			JsonObject loadResponseObject = reader.readObject();
//			ret = new ResponseMessage(Response.Status.fromStatusCode(loadResponse.statusCode), sourceMessage,
//					loadResponseObject);
//
//		} catch (Exception ex) {
//
//			ex.printStackTrace();
//
//			// Create an error response message based on T3Resposne
//			if (null != loadResponse) {
//				ret = createResponseMessage(loadResponse, sourceMessage);
//
//			}
//			// Create a general error response
//			else {
//				TSResponse errorResponse = new TSResponse(Response.Status.INTERNAL_SERVER_ERROR, 0);
//				errorResponse.message = "T3 storage failure : " + ex.getMessage();
//				ret = createResponseMessage(errorResponse, sourceMessage);
//			}
//		}
		return ret;
	}

	private ResponseMessage createResponseMessage(TsResponse tsResponse, ProvenMessage sourceMessage) {
		JsonReader reader = Json.createReader(new StringReader(jsonb.toJson(tsResponse)));
		//JsonReader reader = Json.createReader(new StringReader(""));
		JsonObject loadResponseObject = reader.readObject();
		return new ResponseMessage(Response.Status.fromStatusCode(tsResponse.statusCode), sourceMessage,
				loadResponseObject);
	}

	/*
	 * Load/Store T3 data
	 */
//	private TSResponse loadMessageData(Model dataModel, ProvenMessage sourceMessage) throws Exception {
//
//		TSResponse ret = null;
//
//		// Data streams
//		PipedOutputStream pos = new PipedOutputStream();
//		PipedInputStream pis = new PipedInputStream();
//		pis.connect(pos);
//		
//		try {
//			// Push data to output stream Thread will terminate when run()
//			// completes (i.e. after message is pushed to output pipe)
//			new Thread(new Runnable() {
//				public void run() {
//					//dataModel.write(pos, jenaFormat.toString());
//					dataModel.write(pos);
//					try {
//						pos.close();
//					} catch (IOException e) {
//						e.printStackTrace();
//					}
//				}
//			}).start();
//			ValueFactoryImpl vf = ValueFactoryImpl.getInstance();
//			URI context = vf.createURI("http://" + sourceMessage.getDomain().getDomain());
//			RemoteRepository.AddOp operation = new RemoteRepository.AddOp(pis, addFormat);
//			operation.setContext(context);
//			long t3Count = repo.add(operation);
//			pis.close();
//
//			// Create OK response
//			ret = new TSResponse(Response.Status.OK, t3Count);
//
//		} catch (Exception ex) {
//			log.error("T3 add failure:");
//			ex.printStackTrace();
//			// Create error response
//			ret = new TSResponse(Response.Status.INTERNAL_SERVER_ERROR, 0);
//			ret.message = ex.getMessage();
//		} finally {
//			pos.close();
//			pis.close();
//		}
//
//		return ret;
//	}
}
