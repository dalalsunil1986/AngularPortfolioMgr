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
package ch.xxx.manager.contoller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ch.xxx.manager.connector.HkexConnector;
import ch.xxx.manager.connector.NasdaqConnector;
import ch.xxx.manager.connector.XetraConnector;
import ch.xxx.manager.dto.SymbolDto;
import ch.xxx.manager.service.ComparisonIndex;
import ch.xxx.manager.service.SymbolImportService;
import ch.xxx.manager.service.SymbolService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("rest/symbol")
public class SymbolController {
	@Autowired
	private SymbolImportService importService;
	@Autowired
	private SymbolService service;
	@Autowired
	private NasdaqConnector nasdaqConnector;
	@Autowired
	private HkexConnector hkexConnector;
	@Autowired
	private XetraConnector xetraConnector;

	@GetMapping("/importus/all")
	public Mono<Long> importUsSymbols() {
		return this.importService.importUsSymbols(this.nasdaqConnector.importSymbols());
	}

	@GetMapping("/importhk/all")
	public Mono<Long> importHkSymbols() {
		return this.importService.importHkSymbols(this.hkexConnector.importSymbols());
	}

	@GetMapping("/importde/all")
	public Mono<Long> importDeSymbols() {
		return this.importService.importDeSymbols(this.xetraConnector.importXetraSymbols());
	}

	@GetMapping("/importindex/all")
	public Mono<Long> importIndexSymbols() {
		return this.importService.importReferenceIndexes(Flux.just(ComparisonIndex.SP500.getSymbol(),
				ComparisonIndex.MSCI_CHINA.getSymbol(), ComparisonIndex.EUROSTOXX50.getSymbol()));
	}

	@GetMapping("/all")
	public Flux<SymbolDto> getAllSymbols() {
		return this.service.getAllSymbols();
	}

	@GetMapping("/symbol/{symbol}")
	public Flux<SymbolDto> getSymbolBySymbol(@PathVariable("symbol") String symbol) {
		return this.service.getSymbolBySymbol(symbol);
	}

	@GetMapping("/name/{name}")
	public Flux<SymbolDto> getSymbolByName(@PathVariable("name") String name) {
		return this.service.getSymbolByName(name);
	}
}
