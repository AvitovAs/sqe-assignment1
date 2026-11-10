package ac.il.bgu.qa;

import ac.il.bgu.qa.errors.*;
import ac.il.bgu.qa.services.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the Library class.
 * The Library is tested in isolation - all of its dependencies (services, Book, User) are mocks.
 * Test names: given<Condition>_when<Method>_then<Result>
 */
public class TestLibrary {

    // ISBN-13 values that we calculated by hand to be valid
    private static final String VALID_ISBN = "9780306406157";   // check digit is 7
    private static final String VALID_USER_ID = "123456789012"; // exactly 12 digits
    private static final String VALID_TITLE = "Clean Code";
    private static final String VALID_AUTHOR = "Robert Martin";
    private static final String VALID_USER_NAME = "Asaf";

    @Mock
    private DatabaseService databaseService;
    @Mock
    private ReviewService reviewService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private Book book;
    @Mock
    private User user;

    // the class under test
    private Library library;

    // used to check what the Library prints to the console
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        library = new Library(databaseService, reviewService);

        // default behaviour of the mocks: a valid book and a valid user.
        // each test changes only the thing it wants to check.
        when(book.getISBN()).thenReturn(VALID_ISBN);
        when(book.getTitle()).thenReturn(VALID_TITLE);
        when(book.getAuthor()).thenReturn(VALID_AUTHOR);
        when(book.isBorrowed()).thenReturn(false);

        when(user.getId()).thenReturn(VALID_USER_ID);
        when(user.getName()).thenReturn(VALID_USER_NAME);
        when(user.getNotificationService()).thenReturn(notificationService);

        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    public void tearDown() {
        // give the console back
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    // ------------------------------------------------------------------
    // addBook
    // ------------------------------------------------------------------

    @Test
    public void givenNullBook_whenAddBook_thenThrowIllegalArgumentException() {
        // Act
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> library.addBook(null));

        // Assert
        assertEquals("Invalid book.", e.getMessage());
        verifyNoInteractions(databaseService);   // nothing was read or saved
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "978030640615",      // 12 digits - too short
            "97803064061570",    // 14 digits - too long
            "978030640615a",     // 13 chars but not only digits
            "abcdefghijklm",     // 13 letters
            "9780306406158",     // wrong check digit
            "9780306406150",     // wrong check digit (0 instead of 7)
            "9783161484101",     // wrong check digit (should be 0)
            "9870306406157",     // two digits switched, so the weights matter
            "978-0-306-40615",   // hyphens and too short
            "978 0306406157",    // space is not allowed
            " 9780306406157",    // valid ISBN with a space before it
            "9780306406157 ",    // valid ISBN with a space after it
            "9780306406157\n",   // valid ISBN with a new line after it
            "-------------",     // only hyphens, nothing is left after removing them
            "+978030640615",     // 13 chars with a plus sign
            "978030640615.",
            "0000000000001"      // all zeros with a wrong check digit
    })
    public void givenInvalidISBN_whenAddBook_thenThrowIllegalArgumentException(String isbn) {
        // Arrange
        when(book.getISBN()).thenReturn(isbn);

        // Act
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> library.addBook(book));

        // Assert
        assertEquals("Invalid ISBN.", e.getMessage());
        verifyNoInteractions(databaseService);   // nothing was read or saved
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "9780306406157",        // check digit 7
            "9783161484100",        // sum % 10 == 0, so the check digit is 0
            "978-3-16-148410-0",    // same one with hyphens
            "978-0-306-40615-7",
            "9780000000002",        // lots of zeros
            "0000000000000",        // only zeros - the sum is 0 so the check digit is 0
            "9791234567896",        // starts with 979
            "-978030640615-7",      // hyphen at the start
            "9780306406157-",       // hyphen at the end
            "9-7-8-0-3-0-6-4-0-6-1-5-7"
    })
    public void givenValidISBN_whenAddBook_thenBookAddedToDatabase(String isbn) {
        // Arrange
        when(book.getISBN()).thenReturn(isbn);
        when(databaseService.getBookByISBN(isbn)).thenReturn(null);

        // Act
        library.addBook(book);

        // Assert
        verify(databaseService).addBook(isbn, book);
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void givenInvalidTitle_whenAddBook_thenThrowIllegalArgumentException(String title) {
        // Arrange
        when(book.getTitle()).thenReturn(title);

        // Act
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> library.addBook(book));

        // Assert
        assertEquals("Invalid title.", e.getMessage());
        verifyNoInteractions(databaseService);   // nothing was read or saved
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "1Robert",          // starts with a digit
            "-Robert",          // starts with a special char
            " Robert",          // starts with a space
            "Robert.",          // ends with a dot
            "Robert1",          // ends with a digit
            "Rob3rt",           // digit in the middle
            "Rob@rt",           // char that is not allowed
            "Rob_ert Martin",
            "Robert--Martin",   // consecutive hyphens
            "Robert  Martin",   // consecutive spaces
            "Robert..Martin",   // consecutive dots
            "Robert''Martin",   // consecutive apostrophes
            "7",                // one char that is not a letter
            "Ro#b",             // bad char right before the last char
            "Robert\tMartin",   // tab is not a space
            "Robert\nMartin",
            "Robert, Martin",   // comma is not allowed
            "Robert'",          // ends with an apostrophe
            "Robert Martin ",   // ends with a space
            "R.",               // two chars, ends with a dot
            "--",               // only special chars
            "Robert--",
            "R  M"
    })
    public void givenInvalidAuthor_whenAddBook_thenThrowIllegalArgumentException(String author) {
        // Arrange
        when(book.getAuthor()).thenReturn(author);

        // Act
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> library.addBook(book));

        // Assert
        assertEquals("Invalid author.", e.getMessage());
        verifyNoInteractions(databaseService);   // nothing was read or saved
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Robert Martin",
            "A",                    // one letter only
            "Ab",
            "O'Neil",
            "Jean-Paul Sartre",
            "J.R.R. Tolkien",       // dot and then space is fine, they are different chars
            "Mary-'Ann",            // two different special chars in a row
            "Dr. Seuss",
            "Robert-M",             // special char right before the last letter
            "Rober't",
            "Robert.-Martin",
            "R M",
            "A.-B"                  // two different special chars in a row are fine
    })
    public void givenValidAuthor_whenAddBook_thenBookAddedToDatabase(String author) {
        // Arrange
        when(book.getAuthor()).thenReturn(author);
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(null);

        // Act
        library.addBook(book);

        // Assert
        verify(databaseService).addBook(VALID_ISBN, book);
    }

    @Test
    public void givenBorrowedBook_whenAddBook_thenThrowIllegalArgumentException() {
        // Arrange
        when(book.isBorrowed()).thenReturn(true);

        // Act
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> library.addBook(book));

        // Assert
        assertEquals("Book with invalid borrowed state.", e.getMessage());
        verifyNoInteractions(databaseService);   // nothing was read or saved
    }

    @Test
    public void givenBookAlreadyInDatabase_whenAddBook_thenThrowIllegalArgumentException() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);

        // Act
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> library.addBook(book));

        // Assert
        assertEquals("Book already exists.", e.getMessage());
        verify(databaseService, never()).addBook(any(), any());
    }

    @Test
    public void givenBlankTitle_whenAddBook_thenBookAddedToDatabase() {
        // Arrange - only null and "" are invalid titles, a space is still a title
        when(book.getTitle()).thenReturn(" ");

        // Act
        library.addBook(book);

        // Assert
        verify(databaseService).addBook(VALID_ISBN, book);
    }

    @Test
    public void givenValidNewBook_whenAddBook_thenBookAddedToDatabaseOnce() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(null);

        // Act
        library.addBook(book);

        // Assert
        verify(databaseService).getBookByISBN(VALID_ISBN);
        verify(databaseService, times(1)).addBook(VALID_ISBN, book);
    }

    // ------------------------------------------------------------------
    // registerUser
    // ------------------------------------------------------------------

    @Test
    public void givenNullUser_whenRegisterUser_thenThrowIllegalArgumentException() {
        // Act
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> library.registerUser(null));

        // Assert
        assertEquals("Invalid user.", e.getMessage());
        verifyNoInteractions(databaseService);   // nothing was read or saved
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "12345678901",      // 11 digits
            "1234567890123",    // 13 digits
            "12345678901a",     // 12 chars with a letter
            "abcdefghijkl",
            "123456 789012",
            "-12345678901",
            "+12345678901",
            "123456789012 ",    // valid id with a space after it
            " 123456789012",
            "123456789012\n",   // valid id with a new line after it
            "1234567890.2",
            "12345678901234567890123"   // almost two ids together (23 digits)
    })
    public void givenInvalidUserId_whenRegisterUser_thenThrowIllegalArgumentException(String id) {
        // Arrange
        when(user.getId()).thenReturn(id);

        // Act
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> library.registerUser(user));

        // Assert
        assertEquals("Invalid user Id.", e.getMessage());
        verifyNoInteractions(databaseService);   // nothing was read or saved
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void givenInvalidUserName_whenRegisterUser_thenThrowIllegalArgumentException(String name) {
        // Arrange
        when(user.getName()).thenReturn(name);

        // Act
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> library.registerUser(user));

        // Assert
        assertEquals("Invalid user name.", e.getMessage());
        verifyNoInteractions(databaseService);   // nothing was read or saved
    }

    @Test
    public void givenNullNotificationService_whenRegisterUser_thenThrowIllegalArgumentException() {
        // Arrange
        when(user.getNotificationService()).thenReturn(null);

        // Act
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> library.registerUser(user));

        // Assert
        assertEquals("Invalid notification service.", e.getMessage());
        verifyNoInteractions(databaseService);   // nothing was read or saved
    }

    @Test
    public void givenUserAlreadyRegistered_whenRegisterUser_thenThrowIllegalArgumentException() {
        // Arrange
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);

        // Act
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> library.registerUser(user));

        // Assert
        assertEquals("User already exists.", e.getMessage());
        verify(databaseService, never()).registerUser(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"000000000000", "999999999999", "123456789012"})
    public void givenValidUserId_whenRegisterUser_thenUserRegisteredInDatabase(String id) {
        // Arrange
        when(user.getId()).thenReturn(id);

        // Act
        library.registerUser(user);

        // Assert
        verify(databaseService).registerUser(id, user);
    }

    @Test
    public void givenBlankUserName_whenRegisterUser_thenUserRegisteredInDatabase() {
        // Arrange - only null and "" are invalid names
        when(user.getName()).thenReturn(" ");

        // Act
        library.registerUser(user);

        // Assert
        verify(databaseService).registerUser(VALID_USER_ID, user);
    }

    @Test
    public void givenValidNewUser_whenRegisterUser_thenUserRegisteredInDatabase() {
        // Arrange
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(null);

        // Act
        library.registerUser(user);

        // Assert
        verify(databaseService).getUserById(VALID_USER_ID);
        verify(databaseService, times(1)).registerUser(VALID_USER_ID, user);
    }

    // ------------------------------------------------------------------
    // borrowBook
    // ------------------------------------------------------------------

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"978030640615", "9780306406158", "978030640615a"})
    public void givenInvalidISBN_whenBorrowBook_thenThrowIllegalArgumentException(String isbn) {
        // Act
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> library.borrowBook(isbn, VALID_USER_ID));

        // Assert
        assertEquals("Invalid ISBN.", e.getMessage());
        verify(databaseService, never()).borrowBook(any(), any());
    }

    @Test
    public void givenBookNotInDatabase_whenBorrowBook_thenThrowBookNotFoundException() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(null);

        // Act
        BookNotFoundException e = assertThrows(BookNotFoundException.class,
                () -> library.borrowBook(VALID_ISBN, VALID_USER_ID));

        // Assert
        assertEquals("Book not found!", e.getMessage());
        verify(databaseService, never()).borrowBook(any(), any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"12345678901", "1234567890123", "12345678901a"})
    public void givenInvalidUserId_whenBorrowBook_thenThrowIllegalArgumentException(String userId) {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);

        // Act
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> library.borrowBook(VALID_ISBN, userId));

        // Assert
        assertEquals("Invalid user Id.", e.getMessage());
        verify(book, never()).borrow();
        verify(databaseService, never()).borrowBook(any(), any());
    }

    @Test
    public void givenUserNotRegistered_whenBorrowBook_thenThrowUserNotRegisteredException() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(null);

        // Act
        UserNotRegisteredException e = assertThrows(UserNotRegisteredException.class,
                () -> library.borrowBook(VALID_ISBN, VALID_USER_ID));

        // Assert
        assertEquals("User not found!", e.getMessage());
        verify(book, never()).borrow();
        verify(databaseService, never()).borrowBook(any(), any());
    }

    @Test
    public void givenBookAlreadyBorrowed_whenBorrowBook_thenThrowBookAlreadyBorrowedException() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);
        when(book.isBorrowed()).thenReturn(true);

        // Act
        BookAlreadyBorrowedException e = assertThrows(BookAlreadyBorrowedException.class,
                () -> library.borrowBook(VALID_ISBN, VALID_USER_ID));

        // Assert
        assertEquals("Book is already borrowed!", e.getMessage());
        verify(book, never()).borrow();
        verify(databaseService, never()).borrowBook(any(), any());
    }

    @Test
    public void givenAvailableBookAndRegisteredUser_whenBorrowBook_thenBookBorrowedAndSavedInDatabase() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);

        // Act
        library.borrowBook(VALID_ISBN, VALID_USER_ID);

        // Assert
        verify(book, times(1)).borrow();
        verify(databaseService, times(1)).borrowBook(VALID_ISBN, VALID_USER_ID);
        // first the book is marked as borrowed and only then it is saved
        InOrder inOrder = inOrder(book, databaseService);
        inOrder.verify(book).borrow();
        inOrder.verify(databaseService).borrowBook(VALID_ISBN, VALID_USER_ID);
    }

    @Test
    public void givenISBNWithHyphens_whenBorrowBook_thenDatabaseGetsTheSameISBN() {
        // Arrange
        String isbnWithHyphens = "978-0-306-40615-7";
        when(databaseService.getBookByISBN(isbnWithHyphens)).thenReturn(book);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);

        // Act
        library.borrowBook(isbnWithHyphens, VALID_USER_ID);

        // Assert - the ISBN is passed as it was given, the hyphens are not removed
        verify(databaseService).borrowBook(isbnWithHyphens, VALID_USER_ID);
    }

    // ------------------------------------------------------------------
    // returnBook
    // ------------------------------------------------------------------

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"978030640615", "9780306406158", "978030640615a"})
    public void givenInvalidISBN_whenReturnBook_thenThrowIllegalArgumentException(String isbn) {
        // Act
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> library.returnBook(isbn));

        // Assert
        assertEquals("Invalid ISBN.", e.getMessage());
        verify(databaseService, never()).returnBook(any());
    }

    @Test
    public void givenBookNotInDatabase_whenReturnBook_thenThrowBookNotFoundException() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(null);

        // Act
        BookNotFoundException e = assertThrows(BookNotFoundException.class, () -> library.returnBook(VALID_ISBN));

        // Assert
        assertEquals("Book not found!", e.getMessage());
        verify(databaseService, never()).returnBook(any());
    }

    @Test
    public void givenBookNotBorrowed_whenReturnBook_thenThrowBookNotBorrowedException() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(book.isBorrowed()).thenReturn(false);

        // Act
        BookNotBorrowedException e = assertThrows(BookNotBorrowedException.class,
                () -> library.returnBook(VALID_ISBN));

        // Assert
        assertEquals("Book wasn't borrowed!", e.getMessage());
        verify(book, never()).returnBook();
        verify(databaseService, never()).returnBook(any());
    }

    @Test
    public void givenBorrowedBook_whenReturnBook_thenBookReturnedAndSavedInDatabase() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(book.isBorrowed()).thenReturn(true);

        // Act
        library.returnBook(VALID_ISBN);

        // Assert
        verify(book, times(1)).returnBook();
        verify(databaseService, times(1)).returnBook(VALID_ISBN);
        InOrder inOrder = inOrder(book, databaseService);
        inOrder.verify(book).returnBook();
        inOrder.verify(databaseService).returnBook(VALID_ISBN);
    }

    // ------------------------------------------------------------------
    // notifyUserWithBookReviews
    // ------------------------------------------------------------------

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"978030640615", "9780306406158", "978030640615a"})
    public void givenInvalidISBN_whenNotifyUserWithBookReviews_thenThrowIllegalArgumentException(String isbn) {
        // Act
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> library.notifyUserWithBookReviews(isbn, VALID_USER_ID));

        // Assert
        assertEquals("Invalid ISBN.", e.getMessage());
        verifyNoInteractions(databaseService, reviewService);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"12345678901", "1234567890123", "12345678901a"})
    public void givenInvalidUserId_whenNotifyUserWithBookReviews_thenThrowIllegalArgumentException(String userId) {
        // Act
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> library.notifyUserWithBookReviews(VALID_ISBN, userId));

        // Assert
        assertEquals("Invalid user Id.", e.getMessage());
        verifyNoInteractions(databaseService, reviewService);
    }

    @Test
    public void givenBookNotInDatabase_whenNotifyUserWithBookReviews_thenThrowBookNotFoundException() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(null);

        // Act
        BookNotFoundException e = assertThrows(BookNotFoundException.class,
                () -> library.notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID));

        // Assert
        assertEquals("Book not found!", e.getMessage());
        verifyNoInteractions(reviewService);
    }

    @Test
    public void givenUserNotRegistered_whenNotifyUserWithBookReviews_thenThrowUserNotRegisteredException() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(null);

        // Act
        UserNotRegisteredException e = assertThrows(UserNotRegisteredException.class,
                () -> library.notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID));

        // Assert
        assertEquals("User not found!", e.getMessage());
        verifyNoInteractions(reviewService);
    }

    @Test
    public void givenNullReviews_whenNotifyUserWithBookReviews_thenThrowNoReviewsFoundException() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);
        when(reviewService.getReviewsForBook(VALID_ISBN)).thenReturn(null);

        // Act
        NoReviewsFoundException e = assertThrows(NoReviewsFoundException.class,
                () -> library.notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID));

        // Assert
        assertEquals("No reviews found!", e.getMessage());
        verify(reviewService, times(1)).close();   // the service is closed even when we fail
        verify(user, never()).sendNotification(any());
    }

    @Test
    public void givenEmptyReviews_whenNotifyUserWithBookReviews_thenThrowNoReviewsFoundException() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);
        when(reviewService.getReviewsForBook(VALID_ISBN)).thenReturn(new ArrayList<>());

        // Act
        NoReviewsFoundException e = assertThrows(NoReviewsFoundException.class,
                () -> library.notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID));

        // Assert
        assertEquals("No reviews found!", e.getMessage());
        verify(reviewService, times(1)).close();
        verify(user, never()).sendNotification(any());
    }

    @Test
    public void givenReviewServiceFails_whenNotifyUserWithBookReviews_thenThrowReviewServiceUnavailableException() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);
        when(reviewService.getReviewsForBook(VALID_ISBN)).thenThrow(new ReviewException("service is down"));

        // Act
        ReviewServiceUnavailableException e = assertThrows(ReviewServiceUnavailableException.class,
                () -> library.notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID));

        // Assert
        assertEquals("Review service unavailable!", e.getMessage());
        verify(reviewService, times(1)).close();
        verify(user, never()).sendNotification(any());
    }

    @Test
    public void givenBookWithReviews_whenNotifyUserWithBookReviews_thenNotificationSentOnce() {
        // Arrange
        List<String> reviews = Arrays.asList("Great book", "Too long");
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);
        when(reviewService.getReviewsForBook(VALID_ISBN)).thenReturn(reviews);

        // Act
        library.notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID);

        // Assert
        verify(user, times(1)).sendNotification("Reviews for 'Clean Code':\nGreat book\nToo long");
        verify(reviewService, times(1)).close();
        assertEquals("", errContent.toString());   // no retry messages
    }

    @Test
    public void givenSingleReview_whenNotifyUserWithBookReviews_thenMessageContainsTitleAndReview() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);
        when(reviewService.getReviewsForBook(VALID_ISBN)).thenReturn(Arrays.asList("Nice"));

        // Act
        library.notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID);

        // Assert
        verify(user).sendNotification("Reviews for 'Clean Code':\nNice");
    }

    @Test
    public void givenBookWithReviews_whenNotifyUserWithBookReviews_thenReviewServiceClosedBeforeNotification() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);
        when(reviewService.getReviewsForBook(VALID_ISBN)).thenReturn(Arrays.asList("Nice"));

        // Act
        library.notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID);

        // Assert
        InOrder inOrder = inOrder(reviewService, user);
        inOrder.verify(reviewService).getReviewsForBook(VALID_ISBN);
        inOrder.verify(reviewService).close();
        inOrder.verify(user).sendNotification(anyString());
    }

    @Test
    public void givenReviewServiceThrowsOtherException_whenNotifyUserWithBookReviews_thenSameExceptionAndServiceClosed() {
        // Arrange - an exception that is not a ReviewException should not be replaced
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);
        when(reviewService.getReviewsForBook(VALID_ISBN)).thenThrow(new IllegalStateException("something else"));

        // Act
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> library.notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID));

        // Assert
        assertEquals("something else", e.getMessage());
        verify(reviewService, times(1)).close();
        verify(user, never()).sendNotification(any());
    }

    @Test
    public void givenNotificationThrowsOtherException_whenNotifyUserWithBookReviews_thenNoRetry() {
        // Arrange - only NotificationException should cause a retry
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);
        when(reviewService.getReviewsForBook(VALID_ISBN)).thenReturn(Arrays.asList("Nice"));
        doThrow(new IllegalStateException("something else")).when(user).sendNotification(anyString());

        // Act
        assertThrows(IllegalStateException.class,
                () -> library.notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID));

        // Assert
        verify(user, times(1)).sendNotification(anyString());
    }

    @Test
    public void givenBorrowedBookWithReviews_whenNotifyUserWithBookReviews_thenNotificationStillSent() {
        // Arrange - this method does not care if the book is borrowed
        when(book.isBorrowed()).thenReturn(true);
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);
        when(reviewService.getReviewsForBook(VALID_ISBN)).thenReturn(Arrays.asList("Nice"));

        // Act
        library.notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID);

        // Assert
        verify(user, times(1)).sendNotification("Reviews for 'Clean Code':\nNice");
    }

    @Test
    public void givenNotificationFailsTwice_whenNotifyUserWithBookReviews_thenRetryUntilSuccess() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);
        when(reviewService.getReviewsForBook(VALID_ISBN)).thenReturn(Arrays.asList("Nice"));
        // first two calls fail, the third one works
        doThrow(new NotificationException("fail 1"))
                .doThrow(new NotificationException("fail 2"))
                .doNothing()
                .when(user).sendNotification(anyString());

        // Act
        library.notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID);

        // Assert
        verify(user, times(3)).sendNotification("Reviews for 'Clean Code':\nNice");
        String err = errContent.toString();
        assertTrue(err.contains("Notification failed! Retrying attempt 1/5"));
        assertTrue(err.contains("Notification failed! Retrying attempt 2/5"));
        assertFalse(err.contains("Retrying attempt 3/5"));
    }

    @Test
    public void givenNotificationFailsFourTimes_whenNotifyUserWithBookReviews_thenFifthAttemptSucceeds() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);
        when(reviewService.getReviewsForBook(VALID_ISBN)).thenReturn(Arrays.asList("Nice"));
        // edge case - the last allowed attempt is the one that works
        doThrow(new NotificationException("fail 1"))
                .doThrow(new NotificationException("fail 2"))
                .doThrow(new NotificationException("fail 3"))
                .doThrow(new NotificationException("fail 4"))
                .doNothing()
                .when(user).sendNotification(anyString());

        // Act + Assert
        assertDoesNotThrow(() -> library.notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID));
        verify(user, times(5)).sendNotification(anyString());
    }

    @Test
    public void givenNotificationAlwaysFails_whenNotifyUserWithBookReviews_thenThrowNotificationExceptionAfterFiveAttempts() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);
        when(reviewService.getReviewsForBook(VALID_ISBN)).thenReturn(Arrays.asList("Nice"));
        doThrow(new NotificationException("fail")).when(user).sendNotification(anyString());

        // Act
        NotificationException e = assertThrows(NotificationException.class,
                () -> library.notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID));

        // Assert
        assertEquals("Notification failed!", e.getMessage());
        verify(user, times(5)).sendNotification("Reviews for 'Clean Code':\nNice");
        verify(reviewService, times(1)).close();
        String err = errContent.toString();
        assertTrue(err.contains("Notification failed! Retrying attempt 1/5"));
        assertTrue(err.contains("Notification failed! Retrying attempt 5/5"));
        assertFalse(err.contains("Retrying attempt 6/5"));
    }

    // ------------------------------------------------------------------
    // getBookByISBN
    // ------------------------------------------------------------------

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"978030640615", "9780306406158", "978030640615a"})
    public void givenInvalidISBN_whenGetBookByISBN_thenThrowIllegalArgumentException(String isbn) {
        // Act
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> library.getBookByISBN(isbn, VALID_USER_ID));

        // Assert
        assertEquals("Invalid ISBN.", e.getMessage());
        verifyNoInteractions(databaseService);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"12345678901", "1234567890123", "12345678901a"})
    public void givenInvalidUserId_whenGetBookByISBN_thenThrowIllegalArgumentException(String userId) {
        // Act
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> library.getBookByISBN(VALID_ISBN, userId));

        // Assert
        assertEquals("Invalid user Id.", e.getMessage());
        verifyNoInteractions(databaseService);
    }

    @Test
    public void givenBookNotInDatabase_whenGetBookByISBN_thenThrowBookNotFoundException() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(null);

        // Act
        BookNotFoundException e = assertThrows(BookNotFoundException.class,
                () -> library.getBookByISBN(VALID_ISBN, VALID_USER_ID));

        // Assert
        assertEquals("Book not found!", e.getMessage());
    }

    @Test
    public void givenBorrowedBook_whenGetBookByISBN_thenThrowBookAlreadyBorrowedException() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(book.isBorrowed()).thenReturn(true);

        // Act
        BookAlreadyBorrowedException e = assertThrows(BookAlreadyBorrowedException.class,
                () -> library.getBookByISBN(VALID_ISBN, VALID_USER_ID));

        // Assert
        assertEquals("Book was already borrowed!", e.getMessage());
        verifyNoInteractions(reviewService);   // we never got to the notification part
    }

    @Test
    public void givenAvailableBookWithReviews_whenGetBookByISBN_thenReturnBookAndNotifyUser() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);
        when(reviewService.getReviewsForBook(VALID_ISBN)).thenReturn(Arrays.asList("Nice"));

        // Act
        Book result = library.getBookByISBN(VALID_ISBN, VALID_USER_ID);

        // Assert
        assertSame(book, result);
        verify(user, times(1)).sendNotification("Reviews for 'Clean Code':\nNice");
        assertFalse(outContent.toString().contains("Notification failed!"));
    }

    @Test
    public void givenNotificationProcessFails_whenGetBookByISBN_thenStillReturnBook() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);
        // no reviews -> notifyUserWithBookReviews throws, but getBookByISBN should not care
        when(reviewService.getReviewsForBook(VALID_ISBN)).thenReturn(null);

        // Act
        Book result = library.getBookByISBN(VALID_ISBN, VALID_USER_ID);

        // Assert
        assertSame(book, result);
        verify(user, never()).sendNotification(any());
        assertTrue(outContent.toString().contains("Notification failed!"));
    }

    @Test
    public void givenNotificationAlwaysFails_whenGetBookByISBN_thenStillReturnBookAfterFiveAttempts() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);
        when(reviewService.getReviewsForBook(VALID_ISBN)).thenReturn(Arrays.asList("Nice"));
        doThrow(new NotificationException("fail")).when(user).sendNotification(anyString());

        // Act
        Book result = library.getBookByISBN(VALID_ISBN, VALID_USER_ID);

        // Assert
        assertSame(book, result);
        verify(user, times(5)).sendNotification(anyString());
        assertTrue(outContent.toString().contains("Notification failed!"));
    }

    @Test
    public void givenReviewServiceFails_whenGetBookByISBN_thenStillReturnBook() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);
        when(reviewService.getReviewsForBook(VALID_ISBN)).thenThrow(new ReviewException("service is down"));

        // Act
        Book result = library.getBookByISBN(VALID_ISBN, VALID_USER_ID);

        // Assert
        assertSame(book, result);
        verify(reviewService, times(1)).close();
        assertTrue(outContent.toString().contains("Notification failed!"));
    }

    @Test
    public void givenAvailableBook_whenGetBookByISBN_thenBookStateAndDatabaseNotChanged() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);
        when(reviewService.getReviewsForBook(VALID_ISBN)).thenReturn(Arrays.asList("Nice"));

        // Act
        library.getBookByISBN(VALID_ISBN, VALID_USER_ID);

        // Assert - getting a book is not borrowing it
        verify(book, never()).borrow();
        verify(databaseService, never()).borrowBook(any(), any());
    }

    @Test
    public void givenUserNotRegistered_whenGetBookByISBN_thenStillReturnBook() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(null);

        // Act
        Book result = library.getBookByISBN(VALID_ISBN, VALID_USER_ID);

        // Assert
        assertSame(book, result);
        assertTrue(outContent.toString().contains("Notification failed!"));
    }
}
