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
package gov.pnnl.proven.cluster.lib.module.component;

import java.util.SortedSet;

import gov.pnnl.proven.cluster.lib.module.component.maintenance.operation.MaintenanceOperation;
import gov.pnnl.proven.cluster.lib.module.component.maintenance.operation.MaintenanceOperationResult;
import gov.pnnl.proven.cluster.lib.module.component.maintenance.operation.ScheduleCheck;

/**
 * Identifies {@code ManagedComponent} status operations.
 * 
 * Each operation may change the component's {@code ManagedStatus} value.
 * 
 * @see ManagedComponent, ManagedStatus, StatusOperation
 * 
 * @author d3j766
 *
 */
public interface ManagedStatusOperation {

	/**
	 * Components may request a scale operation be performed by their parent
	 * component. The new component will be of the same type as the component
	 * making the request.
	 * 
	 * @return true if the request was sucessfully submitted, false otherwise.
	 */
	boolean requestScale();

	/**
	 * Component activation.
	 * 
	 * @return true if the component was successfully activated, false otherwise
	 */
	boolean activate();

	/**
	 * Component is deactivated. Deactivation retries are performed on failure.
	 * 
	 * @return true if the component was successfully deactivated, false
	 *         otherwise
	 */
	boolean deactivate();

	/**
	 * Component is set to a {@code ManagedStatus#Failed} state.
	 */
	void fail();

	/**
	 * Component is removed from service.
	 * 
	 */
	void remove();

	/**
	 * Component is deactivated. No deactivation retries are performed for a
	 * suspend operation.
	 */
	void suspend();

	/**
	 * Component is removed from service. Similar to a {@link #remove()}
	 * operation, however, unlike remove shutdown can be applied to a component
	 * having any non-terminal managed status value.
	 * 
	 */
	void shutdown();

	/**
	 * Performs maintenance operations (checks/repairs) for a component. Returns
	 * a {@code MaintenanceOperationResult} representing the result of the
	 * performed operations.
	 * 
	 * @param ops
	 *            the set of maintenance operations to perform.  
	 * 
	 * @return a MaintenanceOperationResult
	 */
	<T extends MaintenanceOperation> MaintenanceOperationResult check(SortedSet<T> ops);

	/**
	 * Performs a special maintenance check specifically for TaskSchedules,
	 * separate from the component defined maintenance checks performed by the
	 * {@link #check(SortedSet)} operation. This allows schedule checks to be
	 * made outside of normal component maintenance.
	 * 
	 * @param ops
	 *            a sorted set of the scheduler check operations
	 * @return a MaintenanceOperationResult
	 */
	MaintenanceOperationResult schedulerCheck(SortedSet<ScheduleCheck> ops);

}
