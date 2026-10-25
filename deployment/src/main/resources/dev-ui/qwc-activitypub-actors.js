import { LitElement, html, css } from 'lit';
import { JsonRpc } from 'jsonrpc';
import '@vaadin/button';

/**
 * Local actors, with each one's actor document and WebFinger answer on demand.
 */
export class QwcActivitypubActors extends LitElement {
    jsonRpc = new JsonRpc(this);

    static styles = css`
        :host { display: flex; flex-direction: column; gap: 1rem; padding: 1rem; }
        table { border-collapse: collapse; }
        th, td { text-align: left; padding: 0.25rem 0.75rem; border-bottom: 1px solid var(--lumo-contrast-10pct); }
        pre { background: var(--lumo-contrast-5pct); padding: 0.75rem; overflow: auto; margin: 0; }
        .error { color: var(--lumo-error-text-color); }
    `;

    static properties = {
        _actors: { state: true },
        _error: { state: true },
        _title: { state: true },
        _detail: { state: true },
    };

    connectedCallback() {
        super.connectedCallback();
        this._refresh();
    }

    render() {
        return html`
            <div><vaadin-button @click=${this._refresh}>Refresh</vaadin-button></div>
            ${this._renderActors()}
            ${this._detail ? html`<div><strong>${this._title}</strong><pre>${this._detail}</pre></div>` : ''}
        `;
    }

    _renderActors() {
        if (this._error) {
            return html`<span class="error">${this._error}</span>`;
        }
        if (!this._actors) {
            return html`<span>Loading...</span>`;
        }
        if (this._actors.length === 0) {
            return html`<span>No local actors. Register one with <code>LocalActors.register(...)</code>.</span>`;
        }
        return html`
            <table>
                <tr><th>Username</th><th>Type</th><th>Name</th><th>Id</th><th></th></tr>
                ${this._actors.map(a => html`
                    <tr>
                        <td>${a.username}${a.instanceActor ? ' (instance)' : ''}</td>
                        <td>${a.type}</td>
                        <td>${a.name ?? ''}</td>
                        <td><code>${a.id}</code></td>
                        <td>
                            <vaadin-button theme="small" @click=${() => this._show('Actor document', a.username, this.jsonRpc.actorDocument({ username: a.username }))}>Document</vaadin-button>
                            <vaadin-button theme="small" @click=${() => this._show('WebFinger', a.username, this.jsonRpc.webfinger({ username: a.username }))}>WebFinger</vaadin-button>
                        </td>
                    </tr>`)}
            </table>`;
    }

    _refresh() {
        this._error = null;
        this.jsonRpc.actors()
            .then(r => { this._actors = r.result; })
            .catch(e => { this._error = errorText(e, 'actors() failed'); });
    }

    _show(what, username, call) {
        this._title = `${what} of ${username}`;
        this._detail = 'Loading...';
        call.then(r => { this._detail = JSON.stringify(r.result, null, 2); })
            .catch(e => { this._detail = errorText(e, 'request failed'); });
    }
}

function errorText(e, fallback) {
    return (e && e.error && e.error.message) || fallback;
}

customElements.define('qwc-activitypub-actors', QwcActivitypubActors);
