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
package gov.pnnl.proven.client.lib.disclosure;

import java.io.Serializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import gov.pnnl.proven.client.lib.disclosure.exception.InvalidDisclosureDomainException;

/**
 * Represents an operation providing a {@code DisclosureDomain}. This interface
 * also defines the Proven domain and Proven's Common domain. Domains are used
 * to identify disclosure sources. If domain information is not provided or
 * missing, the Common domain will be used.
 * 
 * @author d3j766
 *
 */
@FunctionalInterface
public interface DomainProvider extends Serializable {

	static Logger log = LoggerFactory.getLogger(DomainProvider.class);

	static final String COMMON_SUB_DOMAIN = "common";
	static final String PROVEN_DISCLOSURE_DOMAIN = "proven.pnnl.gov";
	static final String PROVEN_DISCLOSURE_DOMAIN_REVERSE = "gov.pnnl.proven";
	static final String COMMON_DISCLOSURE_DOMAIN = COMMON_SUB_DOMAIN + "." + PROVEN_DISCLOSURE_DOMAIN;
	static final String COMMON_DISCLOSURE_DOMAIN_REVERSE = PROVEN_DISCLOSURE_DOMAIN_REVERSE + "." + "common";

	DisclosureDomain getDomain();

	static DisclosureDomain getCommonDomain() {
		try {
			return new DisclosureDomain(COMMON_DISCLOSURE_DOMAIN);
		} catch (InvalidDisclosureDomainException e) {
			log.error("Common domain not defined correctly");
			throw new IllegalArgumentException();
		}
	}
}