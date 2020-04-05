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
package ch.xxx.manager.service;

import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ch.xxx.manager.dto.QuoteDto;
import ch.xxx.manager.entity.DailyQuoteEntity;
import ch.xxx.manager.entity.IntraDayQuoteEntity;
import ch.xxx.manager.repository.DailyQuoteRepository;
import ch.xxx.manager.repository.IntraDayQuoteRepository;
import reactor.core.publisher.Flux;

@Service
public class QuoteService {
	@Autowired
	private DailyQuoteRepository dailyQuoteRepository;
	@Autowired
	private IntraDayQuoteRepository intraDayQuoteRepository;
	
	public Flux<QuoteDto> getDailyQuotes(String symbol) {
		return this.dailyQuoteRepository.findBySymbol(symbol).flatMapSequential(quote -> convert(quote));
	}
	
	public Flux<QuoteDto> getDailyQuotes(String symbol, LocalDate start, LocalDate end) {
		return this.dailyQuoteRepository.findBySymbolAndDayBetween(symbol, start, end).flatMapSequential(quote -> convert(quote));
	}
	
	public Flux<QuoteDto> getIntraDayQuotes(String symbol) {
		return this.intraDayQuoteRepository.findBySymbol(symbol).flatMapSequential(quote -> convert(quote));
	}
	
	private Flux<QuoteDto> convert(IntraDayQuoteEntity entity) {
		return Flux.just(new QuoteDto(entity.getOpen(), entity.getHigh(), entity.getLow(), entity.getClose(), entity.getVolume(), entity.getLocaldatetime(), entity.getSymbol()));
	}
	
	private Flux<QuoteDto> convert(DailyQuoteEntity entity) {
		return Flux.just(new QuoteDto(entity.getOpen(), entity.getHigh(), entity.getLow(), entity.getClose(), entity.getVolume(), entity.getDay().atStartOfDay(), entity.getSymbol()));
	}
}
