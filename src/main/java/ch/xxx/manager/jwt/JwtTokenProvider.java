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
package ch.xxx.manager.jwt;

import java.security.Key;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AuthorizationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import ch.xxx.manager.exception.AuthenticationException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtTokenProvider {

	@Value("${security.jwt.token.secret-key}")
	private String secretKey;

	@Value("${security.jwt.token.expire-length}")
	private long validityInMilliseconds; // 1 min
	
	private Key jwtTokenKey;

	@PostConstruct
	public void init() {
		this.jwtTokenKey = Keys.hmacShaKeyFor(secretKey.getBytes());
	}

	public String createToken(String username, List<Role> roles, Optional<Date> issuedAtOpt) {
		Claims claims = Jwts.claims();
		claims.setSubject(username);
		claims.put(JwtUtils.TOKENAUTHKEY, roles.stream().map(s -> new SimpleGrantedAuthority(s.getAuthority()))
				.filter(Objects::nonNull).collect(Collectors.toList()));
		claims.put(JwtUtils.TOKENLASTMSGKEY, new Date().getTime());
		Date issuedAt = issuedAtOpt.orElse(new Date());
		claims.setIssuedAt(issuedAt);
		Date validity = new Date(issuedAt.getTime() + validityInMilliseconds);
		claims.setExpiration(validity);

		return Jwts.builder().setClaims(claims)
				.signWith(this.jwtTokenKey, SignatureAlgorithm.HS256).compact();
	}

	public String refreshToken(String token) {
		validateToken(token);
		Optional<Jws<Claims>> claimsOpt = this.getClaims(Optional.of(token));
		if(claimsOpt.isEmpty()) {
			throw new AuthorizationServiceException("Invalid token claims");
		}
		Claims claims = claimsOpt.get().getBody();
		claims.setIssuedAt(new Date());
		claims.setExpiration(new Date(Instant.now().toEpochMilli() + validityInMilliseconds));
		String newToken = Jwts.builder().setClaims(claims).signWith(this.jwtTokenKey, SignatureAlgorithm.HS256).compact();
		return newToken;
	}
	
	public Optional<Jws<Claims>> getClaims(Optional<String> token) {
		if (!token.isPresent()) {
			return Optional.empty();
		}
		return Optional.of(Jwts.parserBuilder().setSigningKey(this.jwtTokenKey).build().parseClaimsJws(token.get()));
	}

	public Authentication getAuthentication(String token) {		
		if(this.getAuthorities(token).stream().filter(role -> role.equals(Role.GUEST)).count() > 0) {
			return new UsernamePasswordAuthenticationToken(this.getUsername(token), null);
		}
		return new UsernamePasswordAuthenticationToken(this.getUsername(token), "", this.getAuthorities(token));
	}

	public String getUsername(String token) {
		return Jwts.parserBuilder().setSigningKey(this.jwtTokenKey).build().parseClaimsJws(token).getBody().getSubject();
	}
	
	@SuppressWarnings("unchecked")
	public Collection<Role> getAuthorities(String token) {
		Collection<Role> roles = new LinkedList<>();
		for(Role role :Role.values()) {
			roles.add(role);
		}
		Collection<Map<String,String>> rolestrs = (Collection<Map<String,String>>) Jwts.parserBuilder()
				.setSigningKey(this.jwtTokenKey).build().parseClaimsJws(token).getBody().get("auth");
		return rolestrs.stream()
				.map(str -> roles.stream().filter(r -> r.name().equals(str.getOrDefault(JwtUtils.AUTHORITY, "")))
						.findFirst().orElse(Role.GUEST))
				.collect(Collectors.toList());
	}

	public String resolveToken(HttpServletRequest req) {
		String bearerToken = req.getHeader(JwtUtils.AUTHORIZATION);
		Optional<String> tokenOpt = resolveToken(bearerToken);
		return tokenOpt.isEmpty() ? null : tokenOpt.get();
	}

	public Optional<String> resolveToken(String bearerToken) {
		if (bearerToken != null && bearerToken.startsWith(JwtUtils.BEARER)) {
			return Optional.of(bearerToken.substring(7, bearerToken.length()));
		}
		return Optional.empty();
	}
	
	public boolean validateToken(String token) {
		try {
			Jwts.parserBuilder().setSigningKey(this.jwtTokenKey).build().parseClaimsJws(token);
			return true;
		} catch (JwtException | IllegalArgumentException e) {
			throw new AuthenticationException("Expired or invalid JWT token",e);
		}
	}

}