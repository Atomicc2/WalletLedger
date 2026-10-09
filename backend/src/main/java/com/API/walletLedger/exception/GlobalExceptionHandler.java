package com.API.walletLedger.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tratamento global de exceções da API (Fase 3.3).
 *
 * Por que centralizar em um @RestControllerAdvice?
 * → Sem ele, cada controller precisaria de try/catch repetido. Aqui as exceções
 *   lançadas por qualquer controller (ou service chamado por ele) são convertidas
 *   em respostas HTTP padronizadas num único lugar.
 *
 * Por que usar ProblemDetail (RFC 7807)?
 * → É o formato de erro padronizado para APIs HTTP. Campos fixos:
 *   type, title, status, detail (e instance). O cliente (ex.: Angular futuro)
 *   sempre lê a mensagem em "detail", sem precisar adivinhar o formato do backend.
 *
 * Observação: NÃO adicionamos um @ExceptionHandler(Exception.class) "catch-all" aqui.
 * Um handler genérico capturaria exceções do próprio Spring (ex.: rota inexistente)
 * e as transformaria em 500. O fallback global do Spring Boot continua cuidando disso.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Falha de validação de campos (@Valid nos DTOs) → 400 com o mapa de erros por campo. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationErrors(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Um ou mais campos estão inválidos."
        );
        problem.setTitle("Erro de validação");

        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fieldError ->
            errors.put(fieldError.getField(), fieldError.getDefaultMessage())
        );
        problem.setProperty("errors", errors);

        return problemResponse(HttpStatus.BAD_REQUEST, problem);
    }

    /** Regra de negócio / argumento inválido (ex.: saldo insuficiente, e-mail duplicado) → 400. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Requisição inválida");
        return problemResponse(HttpStatus.BAD_REQUEST, problem);
    }

    /** Estado inconsistente (ex.: conta não está ativa) → 409 Conflict. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> handleIllegalState(IllegalStateException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Conflito de estado");
        return problemResponse(HttpStatus.CONFLICT, problem);
    }

    /** Credenciais inválidas no login → 401 com corpo explicativo (antes era 401 "seco"). */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleBadCredentials(BadCredentialsException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.UNAUTHORIZED,
            "Credenciais inválidas."
        );
        problem.setTitle("Não autorizado");
        return problemResponse(HttpStatus.UNAUTHORIZED, problem);
    }

    /** Monta a resposta no media type próprio do RFC 7807: application/problem+json. */
    private ResponseEntity<ProblemDetail> problemResponse(HttpStatus status, ProblemDetail problem) {
        return ResponseEntity.status(status)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problem);
    }
}
