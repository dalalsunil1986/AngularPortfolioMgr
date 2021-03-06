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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.xxx.manager.connector.HkexConnector;
import ch.xxx.manager.connector.NasdaqConnector;
import ch.xxx.manager.connector.XetraConnector;
import ch.xxx.manager.dto.HkSymbolImportDto;
import ch.xxx.manager.entity.SymbolEntity;
import ch.xxx.manager.entity.SymbolEntity.QuoteSource;
import ch.xxx.manager.entity.SymbolEntity.SymbolCurrency;
import ch.xxx.manager.repository.SymbolRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@Transactional
public class SymbolImportService {
	private static final Logger LOGGER = LoggerFactory.getLogger(SymbolImportService.class);
	@Autowired
	private NasdaqConnector nasdaqConnector;
	@Autowired
	private HkexConnector hkexConnector;
	@Autowired
	private SymbolRepository repository;
	@Autowired
	private XetraConnector xetraConnector;
	@Autowired
	private QuoteImportService quoteImportService;
	private AtomicReference<List<SymbolEntity>> allSymbolEntities = new AtomicReference<List<SymbolEntity>>(
			new ArrayList<>());

	@PostConstruct
	public void init() {
		this.repository.findAll().collectList().subscribe(symbolEnities -> {
			this.allSymbolEntities.set(symbolEnities);
			LOGGER.info("{} symbols updated.", symbolEnities.size());
		});
	}

	@Scheduled(cron = "0 0 1 * * ?")
	public void scheduledImporter() {
		this.importUsSymbols(null).subscribe(count -> LOGGER.info("Import of {} us symbols finished.", count));
		this.importHkSymbols(null).subscribe(count -> LOGGER.info("Import of {} hk symbols finished.", count));
		this.importDeSymbols(null).subscribe(count -> LOGGER.info("Import of {} de symbols finished.", count));
		this.importReferenceIndexes(null)
				.subscribe(count -> LOGGER.info("Import of {} index symbols finished.", count));
	}

	public Mono<Long> importUsSymbols(Flux<String> nasdaq) {
		LOGGER.info("importUsSymbols() called.");
		if (nasdaq != null) {
			return nasdaq.filter(this::filter).flatMap(symbolStr -> this.convert(symbolStr))
					.flatMap(entity -> this.replaceEntity(entity, Optional.empty())).count()
					.doAfterTerminate(() -> this.init());
		}
		return this.nasdaqConnector.importSymbols().filter(this::filter).flatMap(symbolStr -> this.convert(symbolStr))
				.flatMap(entity -> this.replaceEntity(entity, Optional.empty())).count()
				.doAfterTerminate(() -> this.init());
	}

	public Mono<Long> importHkSymbols(Flux<HkSymbolImportDto> hkex) {
		LOGGER.info("importHkSymbols() called.");
		if (hkex != null) {
			return hkex.filter(this::filter).flatMap(myDto -> this.convert(myDto))
					.flatMap(entity -> this.replaceEntity(entity, Optional.empty())).count()
					.doAfterTerminate(() -> this.init());
		}
		return this.hkexConnector.importSymbols().filter(this::filter).flatMap(myDto -> this.convert(myDto))
				.flatMap(entity -> this.replaceEntity(entity, Optional.empty())).count()
				.doAfterTerminate(() -> this.init());
	}

	public Mono<Long> importDeSymbols(Flux<String> xetra) {
		LOGGER.info("importDeSymbols() called.");
		if (xetra != null) {
			return xetra.filter(this::filter).filter(this::filterXetra).flatMap(line -> this.convertXetra(line))
					.groupBy(SymbolEntity::getSymbol).flatMap(group -> group.reduce((a, b) -> a))
					.flatMap(entity -> this.replaceEntity(entity, Optional.empty())).count()
					.doAfterTerminate(() -> this.init());
		}
		return this.xetraConnector.importXetraSymbols().filter(this::filter).filter(this::filterXetra)
				.flatMap(line -> this.convertXetra(line)).groupBy(SymbolEntity::getSymbol)
				.flatMap(group -> group.reduce((a, b) -> a))
				.flatMap(entity -> this.replaceEntity(entity, Optional.empty())).count()
				.doAfterTerminate(() -> this.init());
	}

	public Mono<Long> importReferenceIndexes(Flux<String> symbolStrs) {
		LOGGER.info("importReferenceIndexes() called.");
		if (symbolStrs == null) {
			symbolStrs = Flux.just(ComparisonIndex.SP500.getSymbol(), ComparisonIndex.EUROSTOXX50.getSymbol(),
					ComparisonIndex.MSCI_CHINA.getSymbol());
		}
		final Flux<String> localSymbolStrs = symbolStrs;
		final Set<String> symbolStrsToImport = new HashSet<>();
		final List<SymbolEntity> availiableSymbolEntities = new ArrayList<>();
		return this.repository.findAll().collectList().flux().flatMap(symbolEntities -> {
			availiableSymbolEntities.addAll(symbolEntities);
			return localSymbolStrs;
		}).filter(symbolStr -> Stream.of(ComparisonIndex.values()).map(ComparisonIndex::getSymbol)
				.anyMatch(indexSymbol -> indexSymbol.equalsIgnoreCase(symbolStr)))
				.flatMap(indexSymbol -> upsertSymbolEntity(indexSymbol, availiableSymbolEntities)).map(entity -> {
					symbolStrsToImport.add(entity.getSymbol());
					return entity;
				}).count().doAfterTerminate(() -> {
					this.init();
					symbolStrsToImport.forEach(mySymbolStr -> this.quoteImportService
							.importUpdateDailyQuotes(mySymbolStr).subscribeOn(Schedulers.elastic())
							.subscribe(value -> LOGGER.info("Indexquotes import done for: {}", mySymbolStr)));
				});
	}

	private Mono<SymbolEntity> upsertSymbolEntity(String indexSymbol, List<SymbolEntity> availiableSymbolEntities) {
		ComparisonIndex compIndex = Stream.of(ComparisonIndex.values())
				.filter(index -> index.getSymbol().equalsIgnoreCase(indexSymbol)).findFirst()
				.orElseThrow(() -> new RuntimeException("Unknown indexSymbol: " + indexSymbol));
		SymbolEntity symbolEntity = new SymbolEntity(null, compIndex.getSymbol(), compIndex.getName(),
				compIndex.getCurrency(), compIndex.getSource());
		return this.replaceEntity(symbolEntity, Optional.of(availiableSymbolEntities));
	}

	private Mono<SymbolEntity> replaceEntity(SymbolEntity entity,
			Optional<List<SymbolEntity>> availiableSymbolEntitiesOpt) {
		return Flux
				.fromIterable(availiableSymbolEntitiesOpt.isEmpty() ? this.allSymbolEntities.get()
						: availiableSymbolEntitiesOpt.get())
				.filter(filterEntity -> filterEntity.getSymbol().toLowerCase().equals(entity.getSymbol().toLowerCase()))
				.switchIfEmpty(Mono.just(entity)).flatMap(localEntity -> this.updateEntity(localEntity, entity))
				.flatMap(myEntity -> this.repository.save(myEntity)).single();
	}

	private Mono<SymbolEntity> updateEntity(SymbolEntity dbEntity, SymbolEntity importEntity) {
		if (!dbEntity.equals(importEntity)) {
			dbEntity.setName(importEntity.getName());
			dbEntity.setSymbol(importEntity.getSymbol());
		}
		return Mono.just(dbEntity);
	}

	private Flux<SymbolEntity> convert(HkSymbolImportDto dto) {
		String cutSymbol = dto.getSymbol().trim().length() > 4
				? dto.getSymbol().trim().substring(dto.getSymbol().length() - 4)
				: dto.getSymbol().trim();
		return Flux.just(new SymbolEntity(null, String.format("%s.HK", cutSymbol), dto.getName(), SymbolCurrency.HKD,
				QuoteSource.YAHOO));
	}

	private boolean filter(HkSymbolImportDto dto) {
		long symbol = Long.parseLong(dto.getSymbol());
		return symbol < 10000;
	}

	private boolean filter(String line) {
		if (line.isBlank() || line.contains("ACT Symbol|Security Name|Exchange|")
				|| line.contains("File Creation Time:") || line.contains("Symbol|Security Name|Market Category|")
				|| line.contains("Market:") || line.contains("Date Last Update:")
				|| line.contains("Product Status;Instrument Status;")) {
			return false;
		}
		return true;
	}

	private boolean filterXetra(String line) {
		return line.contains("DEUTSCHLAND") || line.contains("DAX");
	}

	private Mono<SymbolEntity> convertXetra(String symbolLine) {
		String[] strParts = symbolLine.split(";");
		String symbol = String.format("%s.DEX",
				strParts[7].substring(0, strParts[7].length() < 15 ? strParts[7].length() : 15));
		SymbolEntity entity = new SymbolEntity(null, symbol,
				strParts[2].substring(0, strParts[2].length() < 100 ? strParts[2].length() : 100), SymbolCurrency.EUR,
				QuoteSource.ALPHAVANTAGE);
		return Mono.just(entity);
	}

	private Mono<SymbolEntity> convert(String symbolLine) {
		String[] strParts = symbolLine.split("\\|");
		SymbolEntity entity = new SymbolEntity(null,
				strParts[0].substring(0, strParts[0].length() < 15 ? strParts[0].length() : 15),
				strParts[1].substring(0, strParts[1].length() < 100 ? strParts[1].length() : 100), SymbolCurrency.USD,
				QuoteSource.ALPHAVANTAGE);
		return Mono.just(entity);
	}
}
