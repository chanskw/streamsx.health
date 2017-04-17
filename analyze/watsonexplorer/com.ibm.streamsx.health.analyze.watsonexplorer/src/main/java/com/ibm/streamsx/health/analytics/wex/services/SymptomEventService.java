package com.ibm.streamsx.health.analytics.wex.services;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.ibm.streamsx.health.analytics.wex.internal.facetsearch.FacetQuery;
import com.ibm.streamsx.health.analytics.wex.internal.facetsearch.FacetSearch;
import com.ibm.streamsx.health.analytics.wex.internal.facetsearch.ParsedFacetSearchResult;
import com.ibm.streamsx.health.analytics.wex.internal.facetsearch.ParsedFacetSearchResult.Facet;
import com.ibm.streamsx.health.analytics.wex.internal.search.ParsedSearchResult;
import com.ibm.streamsx.health.analytics.wex.internal.search.ParsedSearchResult.Result;
import com.ibm.streamsx.health.ingest.types.model.ClinicalNoteEvent;
import com.ibm.streamsx.topology.context.StreamsContext.Type;
import com.ibm.streamsx.topology.function.Function;
import com.ibm.streamsx.topology.function.Supplier;

/**
 * DESCRIPTION:
 * Queries the specified patient's history and 
 * looks for all mentioned symptoms. Each symptom found is 
 * published as a ClinicalNoteEvent. The queries can be 
 * restricted to within a specific date range (startDate and endDate). 
 * Furthermore, custom query options can be specified (query). 
 * 
 * INPUT JSON:
 * {
 *   "patientId" : "string" // unique ID of patient (REQUIRED)
 *   "query" : "string", // query string (OPTIONAL)
 *   "startDate" : "string", // start date (inclusive), format: YYYY-MM-DD (OPTIONAL)
 *   "endDate" : "string", // end date (exclusive), format: YYYY-MM-DD (OPTIONAL) 
 * }
 * 
 * EXPORTED TOPIC:
 *  SYMPTOM_EVENT_SERVICE = "analytics-symptom-event"
 *
 */
public class SymptomEventService extends AbstractEventService {

	private static final long serialVersionUID = 1L;

	public static final String SERVICE_NAME = "SymptomEventService";
	public static final String SYMPTOM_EVENT_SERVICE = "analytics-symptom-event";
	
	public SymptomEventService(String wexToolkitPath) throws Exception {
		super(SERVICE_NAME, wexToolkitPath);
	}
	
	@Override
	Function<ParsedSearchResult, Iterable<ClinicalNoteEvent>> getEventSearch() {
		return new SymptomEventSearch(getWexHostParam(), getWexPortParam(), getCollectionName());
	}
	
	private static class SymptomEventSearch implements Function<ParsedSearchResult, Iterable<ClinicalNoteEvent>> {
		private static final long serialVersionUID = 1L;

		private FacetSearch facetSearch;
		private Supplier<String> collectionName;
		
		public SymptomEventSearch(Supplier<String> host, Supplier<Integer> port, Supplier<String> collectionName) {
			super();
			this.collectionName = collectionName;
			facetSearch = new FacetSearch(host, port);
		}
		
		@Override
		public Iterable<ClinicalNoteEvent> apply(ParsedSearchResult searchResult) {
			List<ClinicalNoteEvent> events = new ArrayList<ClinicalNoteEvent>();
			
			String queryTemplate = "(*:*) AND docid:%s";
			String facet = "{\"count\" : -1, \"namespace\":\"keyword\",\"id\":\"$.Symptom_Disease_Indicator.Normalized\"}";
			String collectionName = this.collectionName.get();
			
			List<Result> results = searchResult.getApiResponse().getResult();
			for(Result result : results) {
				String docId = result.getId();
				String query = String.format(queryTemplate, docId);
				
				FacetQuery facetQuery = new FacetQuery(query, collectionName, facet);
				ParsedFacetSearchResult facetSearchResult = facetSearch.apply(facetQuery);
				
				List<Facet> facets = facetSearchResult.getFacets();
				for(Facet f : facets) {
					String symptom = f.getLabel();
					
					ClinicalNoteEvent event = new ClinicalNoteEvent();
					event.setEventName(symptom);
					event.setEventType("symptom");
					event.setPatientId(searchResult.getPatientId());
					event.setSource(docId);
					event.setTs(parseTs(result.getDate()));
					event.setUom("unknown");
					event.setValue(0.0);
					
					events.add(event);
				}
			}

			return events;
		}
		
		private long parseTs(String date) {
			return OffsetDateTime
					.parse(date, DateTimeFormatter.ISO_DATE_TIME)
					.toInstant()
					.toEpochMilli();
		}
		
	}
	
	@Override
	public String getPublishedTopic() {
		return SYMPTOM_EVENT_SERVICE;
	}
	
	public static void main(String[] args) throws Exception {
		launchService(args, SymptomEventService.class);
	}

}
