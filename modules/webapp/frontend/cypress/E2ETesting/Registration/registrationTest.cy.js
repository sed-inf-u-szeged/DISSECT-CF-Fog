/**
 * Előfeltétel: Az oldal megnyílik
 * Leírás: A tesztesetek a regisztráció sikerességét ellenőrzi, hogy elmentődnek az adatok az adatbázisba helyes adatok esetén, és hibásan megadott adatoknál hibát dob.
 * R01 - Helyes adatok esetén történő regisztrálás
 * R02 - Helytelen e-mail címmel történő regisztrálás
 * R03 - Helytelen jelszóval történő regisztrálás
 */
describe('Registration test suit',()=>{

    it('R01- Login and Logout with good data test case',()=>{
        cy.visit('/');
        cy.get('#registerButton').click();
        cy.get('#email',{timeout:10000}).clear().type('teszt@teszt.hu');
        cy.get('#password',{timeout:10000}).clear().type('TEszt1234');
        cy.get('mat-select',{timeout:10000}).should('have.text', 'West Herts College');
        cy.get('#submitButton',{timeout:10000}).should('have.text',' Register ');
        cy.get('#submitButton',{timeout:10000}).click();
        cy.get('#registrationSuccesfullText').should('be.visible');
    })
    it('R02- Try to Login with wrong e-mail data test case',()=>{
        cy.visit('/');
        cy.get('#registerButton').click();
        cy.get('#email',{timeout:10000}).clear().type('asdf.hu');
        cy.get('#password',{timeout:10000}).clear().type('df1234');
        cy.get('mat-select',{timeout:10000}).should('have.text', 'West Herts College');
        cy.get('#submitButton',{timeout:10000}).should('have.text',' Register ');
        cy.get('#submitButton',{timeout:10000}).click();
        cy.get('#dataFailedText').should('be.visible');
   
    })
    it('R03- Login and Logout with good data test case',()=>{
        cy.visit('/');
        cy.get('#registerButton').click();
        cy.get('#email',{timeout:10000}).clear().type('asdf@asdf.hu');
        cy.get('#password',{timeout:10000}).clear().type('df1234');
        cy.get('mat-select',{timeout:10000}).should('have.text', 'West Herts College');
        cy.get('#submitButton',{timeout:10000}).should('have.text',' Register ');
        cy.get('#submitButton',{timeout:10000}).click();
        cy.get('#dataFailedText').should('be.visible');
    })
})