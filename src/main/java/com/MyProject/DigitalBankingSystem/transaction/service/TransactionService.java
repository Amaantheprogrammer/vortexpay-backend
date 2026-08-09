package com.MyProject.DigitalBankingSystem.transaction.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import com.MyProject.DigitalBankingSystem.audit.annotation.Auditable;
import com.MyProject.DigitalBankingSystem.audit.entity.AuditableAction;
import com.MyProject.DigitalBankingSystem.audit.entity.EntityType;
import org.modelmapper.ModelMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.MyProject.DigitalBankingSystem.account.entity.Account;
import com.MyProject.DigitalBankingSystem.account.entity.AccountStatus;
import com.MyProject.DigitalBankingSystem.account.repository.AccountRepository;
import com.MyProject.DigitalBankingSystem.exception.AccessDeniedException;
import com.MyProject.DigitalBankingSystem.exception.DuplicateResourceException;
import com.MyProject.DigitalBankingSystem.exception.InsufficientBalanceException;
import com.MyProject.DigitalBankingSystem.exception.InvalidTransactionException;
import com.MyProject.DigitalBankingSystem.exception.ResourceNotFoundException;
import com.MyProject.DigitalBankingSystem.fraud.service.FraudCheckService;
import com.MyProject.DigitalBankingSystem.idempotency.service.IdempotencyService;
import com.MyProject.DigitalBankingSystem.transaction.dto.DepositRequest;
import com.MyProject.DigitalBankingSystem.transaction.dto.TransactionRequest;
import com.MyProject.DigitalBankingSystem.transaction.dto.TransactionResponse;
import com.MyProject.DigitalBankingSystem.transaction.dto.WithdrawRequest;
import com.MyProject.DigitalBankingSystem.transaction.entity.Transaction;
import com.MyProject.DigitalBankingSystem.transaction.entity.TransactionStatus;
import com.MyProject.DigitalBankingSystem.transaction.entity.TransactionType;
import com.MyProject.DigitalBankingSystem.transaction.repository.TransactionRepository;
import com.MyProject.DigitalBankingSystem.user.entity.User;
import com.MyProject.DigitalBankingSystem.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final FraudCheckService fraudCheckService;
    private final IdempotencyService idempotencyService;
    private final ModelMapper modelMapper;

    private static final String TRANSFER = "transfer";
    @SuppressWarnings("unused")
    private static final String WITHDRAW = "withdraw";
    @SuppressWarnings("unused")
    private static final String DEPOSIT = "deposit";

    @Transactional(readOnly = true)
    @Cacheable(value = "transactions", key = "#id")
    public TransactionResponse getTransactionById(Long transactionId) {
        Transaction transaction = getOrThrow(transactionId);
        return modelMapper.map(transaction, TransactionResponse.class);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransactionByReference(String transactionReference) {
        Transaction transaction = transactionRepository.findByTransactionReference(transactionReference)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with reference: " + transactionReference));
        return modelMapper.map(transaction, TransactionResponse.class);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactionsByAccountNumber(String accountNumber, Pageable pageable) {
        Account account = getAccountOrThrow(accountNumber);
        Page<Transaction> transactions = transactionRepository.findBySenderAccountOrReceiverAccountOrderByTransactionAtDesc(
                account,
                account,
                pageable
        );
        return transactions.map(transaction -> modelMapper.map(transaction, TransactionResponse.class));
    }

    @Transactional(readOnly = true)
    public TransactionResponse getMyTransaction(String transactionReference) {
        Transaction transaction = transactionRepository.findByTransactionReference(transactionReference)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with reference: " + transactionReference));

        if (transaction.getTransactionType() == TransactionType.TRANSFER || transaction.getTransactionType() == TransactionType.WITHDRAW) {
            validateUserSecurity(transaction.getSenderAccount().getUser(), getSecuredUser());
        } else if (transaction.getTransactionType() == TransactionType.DEPOSIT) {
            validateUserSecurity(transaction.getReceiverAccount().getUser(), getSecuredUser());
        }
        return modelMapper.map(transaction, TransactionResponse.class);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getMyTransactions(String accountNumber, Pageable pageable) {
        Account account = getAccountOrThrow(accountNumber);
        validateUserSecurity(account.getUser(), getSecuredUser());
        Page<Transaction> transactions = transactionRepository.findBySenderAccountOrReceiverAccountOrderByTransactionAtDesc(
                account,
                account,
                pageable
        );
        return transactions.map(transaction -> modelMapper.map(transaction, TransactionResponse.class));
    }

    @Transactional
    @Auditable(action = AuditableAction.TRANSFER, entityType = EntityType.TRANSACTION)
    public TransactionResponse transfer(String key, TransactionRequest transactionRequest) {
        if (idempotencyService.exists(key, TRANSFER)) {
            throw new DuplicateResourceException("Transaction already processed");
        }

        Account senderAccount = getAccountOrThrow(transactionRequest.getSenderAccountNumber());

        validateUserSecurity(senderAccount.getUser(), getSecuredUser());
        validatePositiveAmount(transactionRequest.getAmount());

        String transactionReference = generateTransactionReference();

        fraudCheckService.checkTransactionLimit(senderAccount, transactionRequest.getAmount(), transactionReference);
        fraudCheckService.checkTransactionFrequency(senderAccount, transactionReference);

        Account receiverAccount = getAccountOrThrow(transactionRequest.getReceiverAccountNumber());
        BigDecimal amount = transactionRequest.getAmount();

        if (transactionRequest.getSenderAccountNumber().equals(transactionRequest.getReceiverAccountNumber())) {
            throw new InvalidTransactionException("Sender and receiver cannot be the same");
        }

        if (senderAccount.getStatus() != AccountStatus.ACTIVE || receiverAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidTransactionException("Inactive accounts cannot participate in money transaction");
        }

        if (senderAccount.getBalance().subtract(amount).compareTo(BigDecimal.ZERO) < 0) {
            throw new InsufficientBalanceException("Insufficient balance in the account: " + senderAccount.getAccountNumber());
        }
        senderAccount.setBalance(senderAccount.getBalance().subtract(amount));
        receiverAccount.setBalance(receiverAccount.getBalance().add(amount));

        accountRepository.save(senderAccount);
        accountRepository.save(receiverAccount);

        Transaction transaction = Transaction.builder()
                .transactionReference(transactionReference)
                .senderAccount(senderAccount)
                .receiverAccount(receiverAccount)
                .amount(amount)
                .transactionType(TransactionType.TRANSFER)
                .status(TransactionStatus.SUCCESS)
                .build();
        Transaction savedTransaction = transactionRepository.save(transaction);

        idempotencyService.save(key, TRANSFER, savedTransaction.getTransactionReference());

        return modelMapper.map(savedTransaction, TransactionResponse.class);
    }

    @Transactional
    @Auditable(action = AuditableAction.DEPOSIT, entityType = EntityType.TRANSACTION)
    public TransactionResponse deposit(DepositRequest depositRequest) {
        Account account = getAccountOrThrow(depositRequest.getAccountNumber());

        validateUserSecurity(account.getUser(), getSecuredUser());
        validatePositiveAmount(depositRequest.getAmount());
        validateActiveAccount(account);

        account.setBalance(account.getBalance().add(depositRequest.getAmount()));
        Account savedAccount = accountRepository.save(account);

        Transaction transaction = Transaction.builder()
                .transactionReference(generateTransactionReference())
                .receiverAccount(savedAccount)
                .amount(depositRequest.getAmount())
                .transactionType(TransactionType.DEPOSIT)
                .status(TransactionStatus.SUCCESS)
                .build();
        Transaction savedTransaction = transactionRepository.save(transaction);

        return modelMapper.map(savedTransaction, TransactionResponse.class);
    }

    @Transactional
    @Auditable(action = AuditableAction.WITHDRAW, entityType = EntityType.TRANSACTION)
    public TransactionResponse withdraw(WithdrawRequest withdrawRequest) {
        Account account = getAccountOrThrow(withdrawRequest.getAccountNumber());

        validateUserSecurity(account.getUser(), getSecuredUser());
        validatePositiveAmount(withdrawRequest.getAmount());
        validateActiveAccount(account);

        String transactionReference = generateTransactionReference();

        fraudCheckService.checkTransactionLimit(account, withdrawRequest.getAmount(), transactionReference);
        fraudCheckService.checkTransactionFrequency(account, transactionReference);

        if (account.getBalance().subtract(withdrawRequest.getAmount()).compareTo(BigDecimal.ZERO) < 0) {
            throw new InsufficientBalanceException("Insufficient balance in your account");
        }
        account.setBalance(account.getBalance().subtract(withdrawRequest.getAmount()));
        Account savedAccount = accountRepository.save(account);
        Transaction transaction = Transaction.builder()
                .transactionReference(transactionReference)
                .senderAccount(savedAccount)
                .amount(withdrawRequest.getAmount())
                .transactionType(TransactionType.WITHDRAW)
                .status(TransactionStatus.SUCCESS)
                .build();
        Transaction savedTransaction = transactionRepository.save(transaction);
        return modelMapper.map(savedTransaction, TransactionResponse.class);
    }

    // Private methods
    private Transaction getOrThrow(Long transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with ID: " + transactionId));
        return transaction;
    }

    private Account getAccountOrThrow(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with account number: " + accountNumber));
        return account;
    }

    private String generateTransactionReference() {
        String reference;
        do {
            String date = LocalDateTime.now().format(DateTimeFormatter.BASIC_ISO_DATE);
            String randomPart = UUID.randomUUID().toString().substring(0,6).toUpperCase();
            reference = "TXN-" + date + "-" + randomPart;
        } while (transactionRepository.existsByTransactionReference(reference));
        return reference;
    }

    private void validateActiveAccount(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidTransactionException("Account is not ACTIVE. Current status:  " + account.getStatus());
        }
    }

    private void validatePositiveAmount(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException("Amount must be greater than zero");
        }
    }

    private User getSecuredUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    private void validateUserSecurity(User user, User securedUser) {
        if (!user.getEmail().equals(securedUser.getEmail())) {
            throw new AccessDeniedException("Cannot access other accounts");
        }
    }
}