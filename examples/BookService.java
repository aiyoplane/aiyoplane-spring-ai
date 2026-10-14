/*
 * Example only — paste into a Spring Boot app that already uses Spring AI tool calling.
 *
 * The @Tool annotation is Spring AI; @AiyoGated is aiyoplane-spring-ai. The @Tool call
 * continues to work exactly as it does today in apps that don't declare @AiyoGated; adding
 * @AiyoGated routes this specific tool through the Composition Boundary.
 */
package com.aiyoplane.springai.examples;

import com.aiyoplane.springai.annotation.AiyoGated;
import com.aiyoplane.springai.config.AiyoContext;
import com.aiyoplane.springai.receipt.ExecutionReceipt;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

@Service
public class BookService {

    // Un-gated: Spring AI runs this exactly as it does today. No AEAP involvement.
    @Tool(description = "Look up a book's title by id")
    public String getBookTitle(String bookId) {
        return "Example Title for " + bookId;
    }

    // Gated: aiyoplane-spring-ai evaluates the RDP, signs a receipt, and only then invokes
    // this method body. On ESCALATE / BLOCK the method body never runs.
    @Tool(description = "Delete a book from the catalog")
    @AiyoGated(actionType = "book_mutation", reasonTemplate = "Delete book {bookId}?")
    public String deleteBookById(String bookId) {
        ExecutionReceipt receipt = AiyoContext.currentReceipt();
        // Record the receipt alongside the real-world action.
        // db.audit().insert("books.delete", bookId, receipt.digest());
        // db.books().delete(bookId);
        return "deleted " + bookId + " (receipt " + (receipt == null ? "none" : receipt.digest()) + ")";
    }
}
