/**
 *    Copyright 2019 Sven Loesekann
   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at
       http://www.apache.org/licenses/LICENSE-2.0
   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package ch.xxx.manager.connector;

import java.net.URI;
import java.net.URISyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import ch.xxx.manager.dto.DailyFxWrapperImportDto;
import ch.xxx.manager.dto.DailyWrapperImportDto;
import ch.xxx.manager.dto.IntraDayWrapperImportDto;
import ch.xxx.manager.entity.SymbolEntity;
import reactor.core.publisher.Mono;

@Component
public class AlphavatageConnector {
	private static final Logger LOGGER = LoggerFactory.getLogger(AlphavatageConnector.class);
	@Value("${api.key:xxx}")
	private String apiKey;
	
	public Mono<IntraDayWrapperImportDto> getTimeseriesIntraDay(String symbol) {
		try {
			return WebClient.create().mutate().exchangeStrategies(ConnectorUtils.createLargeResponseStrategy()).build().get()
				.uri(new URI(String.format("https://www.alphavantage.co/query?function=TIME_SERIES_INTRADAY&symbol=%s&interval=5min&outputsize=full&apikey=%s", symbol, this.apiKey)))				                            
				.retrieve().bodyToMono(IntraDayWrapperImportDto.class);
		} catch (URISyntaxException e) {
			LOGGER.error("getTimeseriesHistory failed.",e);
		}
		return Mono.empty();
	}
	
	public Mono<DailyWrapperImportDto> getTimeseriesDailyHistory(String symbol, boolean fullSeries) {
		try {
			String fullSeriesStr = fullSeries ? "&outputsize=full" : ""; 
			return WebClient.create().mutate().exchangeStrategies(ConnectorUtils.createLargeResponseStrategy()).build().get()
				.uri(new URI(String.format("https://www.alphavantage.co/query?function=TIME_SERIES_DAILY_ADJUSTED&symbol=%s%s&apikey=%s", symbol, fullSeriesStr, this.apiKey)))				                            
				.retrieve().bodyToMono(DailyWrapperImportDto.class);
		} catch (URISyntaxException e) {
			LOGGER.error("getTimeseriesHistory failed.",e);
		}
		return Mono.empty();
	}
	
	public Mono<DailyFxWrapperImportDto> getFxTimeseriesDailyHistory(String to_currency, boolean fullSeries) {
		try {
			final String from_currency = SymbolEntity.SymbolCurrency.EUR.toString();
			String fullSeriesStr = fullSeries ? "&outputsize=full" : ""; 
			return WebClient.create().mutate().exchangeStrategies(ConnectorUtils.createLargeResponseStrategy()).build().get()
				.uri(new URI(String.format("https://www.alphavantage.co/query?function=FX_DAILY&from_symbol=%s&to_symbol=%s%s&apikey=%s", from_currency, to_currency, fullSeriesStr, this.apiKey)))				                            
				.retrieve().bodyToMono(DailyFxWrapperImportDto.class);
		} catch (URISyntaxException e) {
			LOGGER.error("getTimeseriesHistory failed.",e);
		}
		return Mono.empty();
	}
}
